import json
import unittest
from datetime import datetime, timezone
from pathlib import Path

from scripts.dora_metrics import build_metrics, percentile, render_report


UTC = timezone.utc
ROOT = Path(__file__).resolve().parents[1]


class DoraMetricsTests(unittest.TestCase):
    def setUp(self) -> None:
        self.raw = json.loads(
            (ROOT / "tests" / "fixtures" / "dora_sample.json").read_text(encoding="utf-8")
        )
        self.end = datetime(2026, 9, 20, tzinfo=UTC)
        self.start = datetime(2026, 6, 22, tzinfo=UTC)

    def test_sample_metrics(self) -> None:
        result = build_metrics(
            self.raw,
            "KTheES/ArtGuard",
            self.start,
            self.end,
            r"^prod(uction)?$",
            "incident",
            "sample",
        )

        self.assertEqual(result["metrics"]["lead_time_for_changes"]["median_hours"], 2.5)
        self.assertEqual(result["metrics"]["lead_time_for_changes"]["p90_hours"], 5.1)
        self.assertEqual(result["metrics"]["deployment_frequency"]["successful_deployments"], 4)
        self.assertEqual(result["metrics"]["deployment_frequency"]["per_week"], 0.31)
        self.assertEqual(result["metrics"]["mean_time_to_restore"]["mean_hours"], 3.5)
        self.assertEqual(result["metrics"]["change_failure_rate"]["percent"], 33.33)
        self.assertEqual(result["metrics"]["change_failure_rate"]["terminal_deployments"], 6)

    def test_staging_deployment_is_excluded(self) -> None:
        result = build_metrics(
            self.raw,
            "KTheES/ArtGuard",
            self.start,
            self.end,
            r"^prod(uction)?$",
            "incident",
            "sample",
        )
        self.assertEqual(result["metrics"]["deployment_frequency"]["successful_deployments"], 4)

    def test_empty_input_uses_null_not_zero(self) -> None:
        result = build_metrics(
            {"deployments": [], "pulls": [], "issues": []},
            "owner/repository",
            self.start,
            self.end,
            r"^production$",
            "incident",
            "sample",
        )
        self.assertIsNone(result["metrics"]["lead_time_for_changes"]["median_hours"])
        self.assertIsNone(result["metrics"]["mean_time_to_restore"]["mean_hours"])
        self.assertIsNone(result["metrics"]["change_failure_rate"]["percent"])
        self.assertIn("SAMPLE DATA", render_report(result))

    def test_percentile_interpolates(self) -> None:
        self.assertAlmostEqual(percentile([1.5, 2, 3, 6], 0.9), 5.1)


if __name__ == "__main__":
    unittest.main()
