"""Data-quality guards for incomplete logs and nested/cross-process phase interpretation."""
import importlib.util
import unittest
from pathlib import Path

module_path = Path(__file__).resolve().parents[2] / "script/summarize_save_trace.py"
spec = importlib.util.spec_from_file_location("save_summary", module_path)
summary = importlib.util.module_from_spec(spec)
spec.loader.exec_module(summary)


class SummaryTest(unittest.TestCase):
    def test_overlap_is_retained_and_missing_end_is_visible(self):
        common = dict(run_id="run", trace_id=1, pid="11", operation="quit", attempt=1,
                      dropped_events=3, wall_epoch_ms=1000, outcome=None, detail=None)
        def row(event, phase, span, elapsed, **extras):
            return dict(common, event=event, phase=phase, span_id=span,
                        elapsed_ms=elapsed, **extras)
        rows = [row("operation_start", "", 0, 0),
                row("phase_start", "save.work", 1, 1),
                row("phase_start", "file.total", 2, 2),
                row("phase_end", "file.total", 2, 7, start_ms=2, duration_ms=5),
                row("phase_end", "save.work", 1, 9, start_ms=1, duration_ms=8),
                row("phase_start", "quit.native_response", 3, 10)]
        result = summary.summarize(rows, [dict(pid="11", phase="process.exit", wall_epoch_ms=1015, window_sampling_ms=1)])[0]
        self.assertEqual(result["outcome"], "missing_end")
        self.assertEqual(result["unclosed_phases"], ["quit.native_response"])
        self.assertEqual(result["dropped_events"], 3)
        self.assertEqual(result["external_exit_ms"], 15)
        self.assertEqual([p["duration_ms"] for p in result["phases"]], [8, 5])
        self.assertNotIn("summed_duration_ms", result)


if __name__ == "__main__":
    unittest.main()
