import json
import math
import pytest
from evaluation.build_smoke import build
from evaluation.dataset import digest
from evaluation.tune import tune


@pytest.fixture
def inputs(tmp_path):
    # Fresh synthetic software fixture only; never migrate held-out project data.
    manifest = build(tmp_path / "fixture")
    data = json.loads(manifest.read_text())
    for row in data["pairs"]:
        row["split"] = "validation"
    manifest.write_text(json.dumps(data))
    report = dict(schema="artworkguard-evaluation-report-v1", split="validation",
                  dataset_sha256=digest(manifest), synthetic=True, mode="basic",
                  model="fixture", model_revision="fixture", preprocessing="fixture", hash_version="fixture",
                  pairs=[dict(id=r["id"], label=r["label"], category=r["category"],
                              score=.9 if r["label"] else .8) for r in data["pairs"]])
    scores = tmp_path / "scores.json"
    scores.write_text(json.dumps(report))
    return manifest, scores


def test_selection_and_synthetic_gate(inputs):
    result = tune(*inputs)
    assert len(result["candidates"]) == 101
    assert result["diagnostic_candidate"]["threshold"] == .9
    assert result["diagnostic_candidate"]["recall"] == 1
    assert result["review_candidate"] is None
    assert not result["production_ready"]
    assert set(result["blockers"]) == {"synthetic_dataset", "fewer_than_30_pairs_per_class", "missing_required_categories"}


@pytest.mark.parametrize("mutation", ["test", "train", "hash", "duplicate", "missing", "label", "category", "nan", "bool", "metadata"])
def test_invalid_scores(inputs, mutation):
    manifest, path = inputs
    report = json.loads(path.read_text())
    if mutation in {"test", "train"}:
        report["split"] = mutation
    elif mutation == "hash":
        report["dataset_sha256"] = "wrong"
    elif mutation == "duplicate":
        report["pairs"][1] = report["pairs"][0]
    elif mutation == "missing":
        report["pairs"].pop()
    elif mutation == "label":
        report["pairs"][0]["label"] = 1
    elif mutation == "category":
        report["pairs"][0]["category"] = "crop"
    elif mutation in {"nan", "bool"}:
        report["pairs"][0]["score"] = math.nan if mutation == "nan" else True
    else:
        report.pop("model_revision")
    path.write_text(json.dumps(report))
    with pytest.raises(ValueError):
        tune(manifest, path)


@pytest.mark.parametrize("score", [-.1, .5])
def test_no_feasible_candidate(inputs, score):
    manifest, path = inputs
    report = json.loads(path.read_text())
    for row in report["pairs"]:
        row["score"] = score
    path.write_text(json.dumps(report))
    result = tune(manifest, path)
    assert result["diagnostic_candidate"] is None
    assert "no_threshold_meets_precision_target" in result["blockers"]


@pytest.mark.parametrize("target", [0, 1.1, math.nan])
def test_invalid_target(inputs, target):
    with pytest.raises(ValueError):
        tune(*inputs, target)
