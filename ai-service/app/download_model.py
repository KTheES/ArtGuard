import os
from pathlib import Path
from huggingface_hub import snapshot_download
from .config import MODEL_ID, MODEL_REVISION

if __name__ == "__main__":
    target = snapshot_download(
        MODEL_ID, revision=MODEL_REVISION,
        cache_dir=str(Path(os.getenv("AI_MODEL_CACHE", ".model-cache")).resolve()),
        allow_patterns=["config.json", "model.safetensors", "preprocessor_config.json"],
    )
    print(f"Model ready: {MODEL_ID}@{MODEL_REVISION}")
    print(f"Cache snapshot: {target}")
