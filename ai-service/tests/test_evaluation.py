import json
import math
import pytest
from evaluation.build_smoke import build
from evaluation.dataset import load
from evaluation.metrics import metrics, ensemble


def test_confusion_counts_and_rates():
    result = metrics([True,True,False,False], [.9,.1,.8,.2], .75)
    assert [result[k] for k in ("tp","fp","tn","fn")] == [1,1,1,1]
    assert all(result[k] == .5 for k in ("precision","recall","f1","false_positive_rate","false_negative_rate"))


def test_undefined_metrics_are_null_and_threshold_is_inclusive():
    result = metrics([True], [.75], .75)
    assert result["tp"] == 1 and result["false_positive_rate"] is None
    assert metrics([False], [.1], .75)["precision"] is None


@pytest.mark.parametrize("value", [math.nan, math.inf, 1.1])
def test_invalid_scores_rejected(value):
    with pytest.raises(ValueError):
        metrics([True], [value], .75)


def test_smoke_manifest_covers_eight_transformations(tmp_path):
    data = load(build(tmp_path/"data"))
    assert len(data["pairs"]) == 27 and data["synthetic"]
    assert len({r["category"] for r in data["pairs"] if r["label"]}) == 8


@pytest.mark.parametrize("mutation", ["hash", "escape", "split", "duplicate", "unreviewed", "label"])
def test_invalid_dataset_is_rejected(tmp_path, mutation):
    path = build(tmp_path/"data")
    data = json.loads(path.read_text())
    row = data["pairs"][0]
    if mutation == "hash":
        row["reference"]["sha256"] = "0"*64
    elif mutation == "escape":
        row["reference"]["path"] = "../escape.png"
    elif mutation == "split":
        row["split"] = "train"
    elif mutation == "duplicate":
        data["pairs"].append(row)
    elif mutation == "unreviewed":
        data["synthetic"] = False
    else:
        row["label"] = False
    path.write_text(json.dumps(data))
    with pytest.raises(ValueError):
        load(path)


def test_ensemble_matches_backend_policy():
    assert ensemble(.5,1,1) == pytest.approx(.6)
    assert ensemble(.9,0,0) == .9
