from dataclasses import dataclass
from pathlib import Path
import os

MODEL_ID = "facebook/dinov2-base"
MODEL_REVISION = "f9e44c814b77203eaa57a6bdbbd535f21ede1415"
PREPROCESSING_VERSION = "rgb-white-bicubic256-center224-imagenet-cls-l2-v1"


@dataclass(frozen=True)
class Settings:
    api_key: str
    allowed_origins: tuple[str, ...] = ()
    allow_private_images: bool = False
    cache_dir: str = ".model-cache"
    device: str = "cpu"
    max_image_bytes: int = 64 * 1024 * 1024
    max_pixels: int = 16_000_000
    download_timeout: float = 15.0
    torch_threads: int = 4

    def __post_init__(self):
        if len(self.api_key) < 32:
            raise ValueError("AI_API_KEY must contain at least 32 characters")
        if self.device not in ("cpu", "cuda"):
            raise ValueError("AI_DEVICE must be cpu or cuda")
        if not 1 <= self.torch_threads <= 32:
            raise ValueError("AI_TORCH_THREADS must be between 1 and 32")
        if not 1 <= self.max_image_bytes <= 64 * 1024 * 1024:
            raise ValueError("Image byte limit must be at most 64 MiB")

    @classmethod
    def from_env(cls):
        return cls(
            api_key=os.getenv("AI_API_KEY", ""),
            allowed_origins=tuple(x.strip() for x in os.getenv("AI_ALLOWED_IMAGE_ORIGINS", "").split(",") if x.strip()),
            allow_private_images=os.getenv("AI_ALLOW_PRIVATE_IMAGES", "false").lower() == "true",
            cache_dir=str(Path(os.getenv("AI_MODEL_CACHE", ".model-cache")).resolve()),
            device=os.getenv("AI_DEVICE", "cpu"),
            torch_threads=int(os.getenv("AI_TORCH_THREADS", "4")),
        )

    def __repr__(self):
        return "Settings(api_key=[REDACTED])"
