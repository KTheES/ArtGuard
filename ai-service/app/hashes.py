import numpy as np
from PIL import Image

PERCEPTUAL_HASH_VERSION = "phash32-dhash9-luma-v1"

def _hex(bits: np.ndarray) -> str:
    value = 0
    for bit in bits.reshape(-1):
        value = (value << 1) | int(bit)
    return f"{value:016x}"

def perceptual_hashes(image: Image.Image) -> tuple[str, str]:
    gray32 = np.asarray(image.convert("L").resize((32, 32), Image.Resampling.LANCZOS), dtype=np.float64)
    positions = np.arange(32, dtype=np.float64)
    frequencies = np.arange(8, dtype=np.float64)[:, None]
    cosine = np.cos(np.pi * (positions + 0.5) * frequencies / 32.0)
    low = cosine @ gray32 @ cosine.T
    threshold = np.median(low.reshape(-1)[1:])
    phash = _hex(low >= threshold)

    gray9 = np.asarray(image.convert("L").resize((9, 8), Image.Resampling.LANCZOS), dtype=np.int16)
    dhash = _hex(gray9[:, :-1] > gray9[:, 1:])
    return phash, dhash
