from pathlib import Path
import numpy as np
from PIL import Image
from .config import Settings, MODEL_ID, MODEL_REVISION
from .errors import ServiceError


def preprocess(image: Image.Image) -> np.ndarray:
    # Matches the pinned BitImageProcessor configuration: shortest edge 256, center crop 224.
    width, height = image.size
    if width <= height:
        new_size = (256, int(height * 256 / width))
    else:
        new_size = (int(width * 256 / height), 256)
    if new_size[0] * new_size[1] > 4_194_304:
        raise ServiceError("INVALID_IMAGE", "The image aspect ratio exceeds the preprocessing limit.")
    resized = image.resize(new_size, Image.Resampling.BICUBIC)
    left, top = (new_size[0] - 224) // 2, (new_size[1] - 224) // 2
    crop = resized.crop((left, top, left + 224, top + 224))
    array = np.asarray(crop, dtype=np.float32) / 255.0
    array = (array - np.array([0.485, 0.456, 0.406], dtype=np.float32)) / np.array([0.229, 0.224, 0.225], dtype=np.float32)
    return np.ascontiguousarray(array.transpose(2, 0, 1)[None, ...])


class DinoEngine:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.ready = False
        self.model = None

    def load(self):
        if self.ready:
            return
        import torch
        from transformers import AutoModel
        torch.set_num_threads(self.settings.torch_threads)
        if self.settings.device == "cuda" and not torch.cuda.is_available():
            raise RuntimeError("CUDA was requested but is unavailable")
        self.model = AutoModel.from_pretrained(
            MODEL_ID, revision=MODEL_REVISION, cache_dir=str(Path(self.settings.cache_dir)),
            local_files_only=True, trust_remote_code=False, use_safetensors=True,
        ).to(self.settings.device).eval()
        if self.model.config.hidden_size != 768:
            raise RuntimeError("Unexpected DINOv2 hidden size")
        self.ready = True

    def embed(self, image: Image.Image) -> list[float]:
        return self.embed_many([image])[0]

    def embed_many(self, images: list[Image.Image]) -> list[list[float]]:
        import torch
        if not self.ready:
            raise ServiceError("MODEL_NOT_READY", "The embedding model is not ready.", 503)
        if not images or len(images) > 8:
            raise ServiceError("INVALID_IMAGE", "Between one and eight image regions are required.")
        pixels = torch.from_numpy(np.concatenate([preprocess(image) for image in images], axis=0)).to(self.settings.device)
        with torch.inference_mode():
            vector = self.model(pixel_values=pixels).last_hidden_state[:, 0, :].float()
            norm = torch.linalg.vector_norm(vector, dim=-1, keepdim=True)
            if not torch.isfinite(vector).all() or (norm <= 0).any():
                raise ServiceError("INFERENCE_FAILED", "The model returned an invalid vector.", 500)
            vector = vector / norm
        return vector.cpu().tolist()
