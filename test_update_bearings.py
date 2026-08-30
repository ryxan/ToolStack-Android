import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

import update_bearings


def _call_add_record(by_desig, sources, desig, d, D, B, source=""):
    """Call add_record while suppressing progress output for unit tests."""
    with contextlib.redirect_stdout(io.StringIO()):
        update_bearings.add_record(by_desig, sources, desig, d, D, B, source)


class TestLoadExisting(unittest.TestCase):
    def test_load_existing_marks_source_as_existing(self):
        with tempfile.TemporaryDirectory() as tmp:
            asset = Path(tmp) / "bearings.json"
            asset.write_text(
                json.dumps(
                    [
                        {
                            "designation": "6205",
                            "type": "Single-row deep groove ball bearing",
                            "boreMm": 25.0,
                            "odMm": 52.0,
                            "widthMm": 15.0,
                            "series": "6200",
                        }
                    ]
                )
            )
            by_desig, sources = update_bearings.load_existing(asset)
            self.assertIn("6205", by_desig)
            self.assertEqual("existing", sources["6205"])


class TestAddRecord(unittest.TestCase):
    def test_adds_new_record(self):
        by_desig = {}
        sources = {}
        _call_add_record(by_desig, sources, "6205", 25.0, 52.0, 15.0, "timken_page_26.txt")
        self.assertEqual("6205", by_desig["6205"]["designation"])
        self.assertEqual(25.0, by_desig["6205"]["boreMm"])
        self.assertEqual("timken_page_26.txt", sources["6205"])

    def test_ignores_identical_record(self):
        by_desig = {}
        sources = {}
        _call_add_record(by_desig, sources, "6205", 25.0, 52.0, 15.0, "existing")
        _call_add_record(by_desig, sources, "6205", 25.0, 52.0, 15.0, "timken_page_26.txt")
        self.assertEqual("existing", sources["6205"])

    def test_replaces_existing_record_with_higher_precedence(self):
        """A parsed source with different dimensions must update a stale existing entry."""
        by_desig = {}
        sources = {}
        _call_add_record(by_desig, sources, "6205", 25.0, 52.0, 15.0, "existing")
        _call_add_record(by_desig, sources, "6205", 25.0, 52.0, 15.5, "timken_page_26.txt")
        self.assertEqual(15.5, by_desig["6205"]["widthMm"])
        self.assertEqual("timken_page_26.txt", sources["6205"])

    def test_keeps_existing_record_with_lower_precedence(self):
        by_desig = {}
        sources = {}
        _call_add_record(by_desig, sources, "6205", 25.0, 52.0, 15.5, "timken_page_26.txt")
        _call_add_record(by_desig, sources, "6205", 25.0, 52.0, 15.0, "existing")
        self.assertEqual(15.5, by_desig["6205"]["widthMm"])
        self.assertEqual("timken_page_26.txt", sources["6205"])

    def test_hardcoded_overrides_parsed(self):
        by_desig = {}
        sources = {}
        _call_add_record(by_desig, sources, "6204", 20.0, 42.0, 12.0, "timken_page_29.txt")
        _call_add_record(by_desig, sources, "6204", 20.0, 42.0, 13.0, "hardcoded")
        self.assertEqual(13.0, by_desig["6204"]["widthMm"])
        self.assertEqual("hardcoded", sources["6204"])

    def test_same_precedence_conflict_fails(self):
        by_desig = {}
        sources = {}
        _call_add_record(by_desig, sources, "6205", 25.0, 52.0, 15.0, "timken_page_26.txt")
        with self.assertRaises(SystemExit):
            _call_add_record(by_desig, sources, "6205", 25.0, 52.0, 15.5, "pti_6900_page_1.txt")

    def test_rejects_non_positive_dimensions(self):
        by_desig = {}
        sources = {}
        _call_add_record(by_desig, sources, "6205", 0.0, 52.0, 15.0, "timken_page_26.txt")
        self.assertNotIn("6205", by_desig)

    def test_rejects_nan_dimensions(self):
        by_desig = {}
        sources = {}
        _call_add_record(by_desig, sources, "6205", float("nan"), 52.0, 15.0, "timken_page_26.txt")
        self.assertNotIn("6205", by_desig)


if __name__ == "__main__":
    unittest.main()
