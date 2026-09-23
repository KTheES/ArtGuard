"""python -m evaluation.run DATASET --output REPORT --split test [--advanced]."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import numpy as np
from PIL import Image, ImageOps
from app.config import Settings, MODEL_ID, MODEL_REVISION, PREPROCESSING_VERSION
from app.engine import DinoEngine
from app.hashes import perceptual_hashes, PERCEPTUAL_HASH_VERSION
from app.regions import crop_regions, REGION_SCHEME
from .dataset import load, digest, REQUIRED
from .metrics import metrics, ensemble


def read_image(path):
    with Image.open(path) as raw:
        if raw.width * raw.height > 16_000_000 or min(raw.size) < 16:
            raise ValueError("Image size outside evaluation limits")
        raw.load()
        rgba = ImageOps.exif_transpose(raw).convert("RGBA")
        white = Image.new("RGBA", rgba.size, "white")
        return Image.alpha_composite(white, rgba).convert("RGB")


def similarity(engine, reference, candidate, advanced):
    images = [candidate]
    if advanced:
        images += [image for _, image in crop_regions(candidate)]
    vectors = engine.embed_many([reference] + images)
    ref_hash = perceptual_hashes(reference)
    scores = []
    for image, vector in zip(images, vectors[1:]):
        cosine = float(np.clip(np.dot(vectors[0], vector), -1, 1))
        if advanced:
            hashes = perceptual_hashes(image)
            p, d = [1 - (int(a,16) ^ int(b,16)).bit_count()/64 for a,b in zip(ref_hash, hashes)]
            cosine = ensemble(cosine, p, d)
        scores.append(cosine)
    return max(scores)


def evaluate(manifest, engine, *, split="test", threshold=0.75, advanced=False):
    data = load(manifest)
    rows = [row for row in data["pairs"] if row["split"] == split]
    if not rows:
        raise ValueError("Selected split is empty")
    root = Path(manifest).resolve().parent
    results = []
    for row in rows:
        score = similarity(engine, read_image(root/row["reference"]["path"]),
                           read_image(root/row["candidate"]["path"]), advanced)
        results.append(dict(id=row["id"], category=row["category"], label=row["label"], score=score))
    def summarize(items):
        return metrics([x["label"] for x in items], [x["score"] for x in items], threshold)
    missing = sorted(REQUIRED - {x["category"] for x in results})
    return {
        "schema": "artworkguard-evaluation-report-v1", "created_at": datetime.now(timezone.utc).isoformat(),
        "dataset_sha256": digest(manifest), "synthetic": data["synthetic"],
        "production_evidence": False,
        "limitations": ["Pair-level metrics; not marketplace prevalence or legal infringement judgments.",
                       "Human review and representative independent data required for production conclusions."],
        "split": split, "threshold": threshold, "mode": "advanced" if advanced else "basic",
        "model": MODEL_ID, "model_revision": MODEL_REVISION, "preprocessing": PREPROCESSING_VERSION,
        "hash_version": PERCEPTUAL_HASH_VERSION, "region_scheme": REGION_SCHEME if advanced else None,
        "missing_categories": missing, "overall": summarize(results),
        "by_category": {c: summarize([x for x in results if x["category"] == c])
                        for c in sorted({x["category"] for x in results})}, "pairs": results,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dataset", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--split", choices=["train", "validation", "test"], default="test")
    parser.add_argument("--threshold", type=float, default=.75)
    parser.add_argument("--advanced", action="store_true")
    parser.add_argument("--model-cache", default=".model-cache")
    args = parser.parse_args()
    if args.output.exists():
        parser.error("Output already exists; choose a new report path")
    # Validate input and threshold before loading expensive local-only model.
    load(args.dataset)
    metrics([True], [1.0], args.threshold)
    settings = Settings(api_key="offline-evaluation-no-network-key", cache_dir=args.model_cache)
    engine = DinoEngine(settings)
    engine.load()
    report = evaluate(args.dataset, engine, split=args.split, threshold=args.threshold, advanced=args.advanced)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, allow_nan=False), encoding="utf-8")
    print(json.dumps(report["overall"]))


if __name__ == "__main__":
    main()
