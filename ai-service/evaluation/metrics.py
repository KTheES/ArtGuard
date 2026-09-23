import math


def metrics(labels, scores, threshold):
    if not math.isfinite(threshold) or not 0 <= threshold <= 1:
        raise ValueError("Threshold must be finite and in [0,1]")
    if len(labels) != len(scores) or not labels:
        raise ValueError("Nonempty aligned labels and scores required")
    counts = dict(tp=0, fp=0, tn=0, fn=0)
    for label, score in zip(labels, scores):
        if type(label) is not bool or not math.isfinite(score) or not -1 <= score <= 1:
            raise ValueError("Invalid label or score")
        counts[("tp" if label else "fp") if score >= threshold else ("fn" if label else "tn")] += 1
    tp, fp, tn, fn = (counts[k] for k in ("tp", "fp", "tn", "fn"))
    def ratio(a, b):
        return a / b if b else None
    return dict(counts, count=len(labels), precision=ratio(tp, tp+fp), recall=ratio(tp, tp+fn),
                f1=ratio(2*tp, 2*tp+fp+fn), false_positive_rate=ratio(fp, fp+tn),
                false_negative_rate=ratio(fn, fn+tp))


def ensemble(cosine, phash, dhash):
    return max(cosine, 0.8*max(0, cosine) + 0.1*phash + 0.1*dhash)
