"""Build deterministic geometric software-test fixtures, never a production benchmark."""
import argparse
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageEnhance, ImageOps
from .dataset import digest


def source(index):
    image = Image.new("RGB", (256,256), "#f5ede0")
    draw = ImageDraw.Draw(image)
    if index == 0:
        draw.ellipse((35,30,210,215), fill="#24395e")
        draw.polygon([(50,75),(115,10),(115,110)], fill="#eb812a")
        draw.rectangle((75,140,230,170), fill="#f0c443")
    elif index == 1:
        draw.polygon([(128,20),(25,220),(240,220)], fill="#2b7852")
        draw.rectangle((115,100,150,250), fill="#8d432b")
        draw.ellipse((165,25,220,80), fill="#e6b431")
    else:
        draw.rectangle((25,40,230,205), fill="#772c65")
        for x in range(40,220,40):
            draw.line((x,10,256-x,240), fill="#47c2c7", width=13)
    return image


def variants(image):
    mockup = Image.new("RGB", (320,320), "#cccccc")
    mockup.paste(image.resize((160,160)), (80,100))
    watermarked = image.copy()
    ImageDraw.Draw(watermarked).text((40,120), "SMOKE FIXTURE", fill="white", stroke_width=2, stroke_fill="black")
    partial = Image.new("RGB", image.size, "white")
    partial.paste(image.crop((64,64,192,192)), (64,64))
    return {
        "original": image.copy(), "crop": image.crop((32,32,224,224)),
        "rotate": image.rotate(25, resample=Image.Resampling.BICUBIC, fillcolor="white"),
        "color_change": ImageEnhance.Color(image).enhance(.2),
        "mockup": mockup, "watermark": watermarked, "partial": partial,
        "mirror": ImageOps.mirror(image),
    }


def build(output):
    output = Path(output)
    output.mkdir(parents=True, exist_ok=False)
    images = output/"images"
    images.mkdir()
    originals = []
    for i in range(3):
        file = images/f"source-{i}.png"
        source(i).save(file)
        originals.append(file)
    def asset(file, group):
        return dict(path=file.relative_to(output).as_posix(), sha256=digest(file), source_group=group)
    rows = []
    for i, file in enumerate(originals):
        for category, image in variants(source(i)).items():
            candidate = images/f"{i}-{category}.png"
            image.save(candidate)
            rows.append(dict(id=f"{i}-{category}", label=True, category=category,
                             reference=asset(file,f"shape-{i}"), candidate=asset(candidate,f"shape-{i}")))
        other = (i+1)%3
        rows.append(dict(id=f"{i}-unrelated", label=False, category="unrelated",
                         reference=asset(file,f"shape-{i}"), candidate=asset(originals[other],f"shape-{other}")))
    for row in rows:
        row.update(split="test", provenance="Procedural geometric software fixture v1",
                   rights="Project-generated test fixture", reviewer="synthetic-generator-v1", review_status="synthetic")
    manifest = output/"dataset.json"
    manifest.write_text(json.dumps(dict(schema="artworkguard-evaluation-v1", synthetic=True, pairs=rows),
                                   indent=2), encoding="utf-8")
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    print(build(parser.parse_args().output))
