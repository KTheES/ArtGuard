"""Opt-in CPU model evaluation for artwork inserted into synthetic products."""
import os
import numpy as np
import pytest
from PIL import Image, ImageDraw
from app.config import Settings
from app.engine import DinoEngine
from app.regions import crop_regions

pytestmark = pytest.mark.model

def artwork():
    image = Image.new("RGB", (256, 256), "#f7e6bb")
    draw = ImageDraw.Draw(image)
    draw.ellipse((38, 28, 218, 208), fill="#14213d", outline="#e63946", width=14)
    draw.polygon(((128, 45), (158, 105), (225, 112), (176, 160), (190, 226), (128, 194), (66, 226), (80, 160), (31, 112), (98, 105)), fill="#fca311")
    draw.rectangle((94, 104, 162, 174), fill="#2a9d8f", outline="white", width=8)
    return image

def products(source):
    shirt = Image.new("RGB", (512, 512), "white")
    draw = ImageDraw.Draw(shirt)
    draw.polygon(((105, 90), (185, 48), (327, 48), (407, 90), (365, 180), (335, 155), (335, 470), (177, 470), (177, 155), (147, 180)), fill="#31587a")
    shirt.paste(source.resize((170, 170), Image.Resampling.LANCZOS), (171, 174))
    mug = Image.new("RGB", (512, 512), "#ece8df")
    draw = ImageDraw.Draw(mug)
    draw.rounded_rectangle((92, 102, 390, 420), 35, fill="#fafafa", outline="#8d99ae", width=10)
    draw.ellipse((338, 175, 475, 345), outline="#8d99ae", width=24)
    mug.paste(source.resize((150, 150), Image.Resampling.LANCZOS), (170, 184))
    return shirt, mug

def test_regions_raise_similarity_for_synthetic_shirt_and_mug():
    settings = Settings(api_key="region-model-test-0123456789abcdef", cache_dir=os.getenv("AI_MODEL_CACHE", ".model-cache"))
    engine = DinoEngine(settings)
    engine.load()
    source = artwork()
    reference = np.asarray(engine.embed(source))
    for name, product in zip(("shirt", "mug"), products(source)):
        vectors = engine.embed_many([product] + [crop for _, crop in crop_regions(product)])
        scores = [float(np.dot(reference, np.asarray(vector))) for vector in vectors]
        print(f"{name}: full={scores[0]:.4f}, best_region={max(scores[1:]):.4f}, gain={max(scores[1:])-scores[0]:.4f}")
        assert max(scores[1:]) > scores[0] + 0.03, scores
