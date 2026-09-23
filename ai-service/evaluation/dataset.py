import hashlib
import json
from pathlib import Path

POSITIVE = {"original", "crop", "rotate", "color_change", "mockup", "watermark", "partial", "mirror"}
NEGATIVE = {"similar_style", "same_character_type", "similar_color", "unrelated"}
REQUIRED = POSITIVE | (NEGATIVE - {"unrelated"})


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def load(path):
    """Validate every record, including records outside the selected evaluation split."""
    path = Path(path).resolve()
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("schema") != "artworkguard-evaluation-v1" or type(data.get("synthetic")) is not bool:
        raise ValueError("Unsupported dataset schema")
    pairs = data.get("pairs")
    if not isinstance(pairs, list) or not pairs:
        raise ValueError("Dataset must contain pairs")
    seen, groups, hashes = set(), {}, {}
    for row in pairs:
        if not isinstance(row.get("id"), str) or not row["id"] or row["id"] in seen:
            raise ValueError("Pair IDs must be nonempty and unique")
        seen.add(row["id"])
        if type(row.get("label")) is not bool:
            raise ValueError("label must be boolean")
        if row.get("category") not in (POSITIVE if row["label"] else NEGATIVE):
            raise ValueError("Category does not match label")
        split = row.get("split")
        if split not in {"train", "validation", "test"}:
            raise ValueError("Invalid split")
        for side in ("reference", "candidate"):
            asset = row[side]
            relative = Path(asset["path"])
            file = (path.parent / relative).resolve()
            if relative.is_absolute() or not file.is_relative_to(path.parent):
                raise ValueError("Asset must stay inside the dataset directory")
            group = asset.get("source_group")
            if not isinstance(group, str) or not group:
                raise ValueError("source_group required")
            if groups.setdefault(group, split) != split:
                raise ValueError("Source-group leakage across splits")
            checksum = digest(file)
            if checksum != asset.get("sha256"):
                raise ValueError("Asset checksum mismatch")
            if hashes.setdefault(checksum, split) != split:
                raise ValueError("Image leakage across splits")
        for key in ("provenance", "rights", "reviewer"):
            if not isinstance(row.get(key), str) or not row[key].strip():
                raise ValueError(f"{key} required")
        if row.get("review_status") not in {"reviewed", "synthetic"}:
            raise ValueError("Unreviewed pair")
        if not data["synthetic"] and row["review_status"] != "reviewed":
            raise ValueError("Real datasets require human-reviewed labels")
    return data
