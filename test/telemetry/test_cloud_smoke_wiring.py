"""Cloud entrypoint routing and privacy boundaries; no private-tree cloud run."""
import contextlib
import importlib.util
import io
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock


ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("cloud_smoke", ROOT / "script/check_cloud.py")
cloud = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cloud)

SAVE_CHECKS = {
    "SaveOptimizationHashProbe", "SessionCompactCodecProbe", "SessionPrimitiveArraysProbe",
    "SaveTraceProbe", "DoodleCoordinatesProbe", "SaveAlgorithmProbe",
    "SettingsPersistenceProbe", "SessionPersistenceProbe", "ReplayLongTextProbe",
    "OwnedProbeInputsProbe", "ProbeInputContractProbe",
}


class CloudSmokeWiringTest(unittest.TestCase):
    def test_core_entrypoint_runs_save_checks_headless_after_privacy(self):
        events = []

        def compile_fixture(jdk, folder):
            events.append("compile")
            jar, tests = folder / "Hodoku.jar", folder / "test-classes"
            jar.write_bytes(b"synthetic compiler result")
            tests.mkdir()
            return jar, tests

        def run_fixture(command, headless, jdk, report, name, timeout):
            events.append((name, headless))
            (report / (name + ".log")).write_text("Usage: java\n" if name == "JarHelp" else "")
            return {"name": name, "exit_code": 0, "timeout": False, "seconds": 0}

        with tempfile.TemporaryDirectory(prefix="hodoku-cloud-routing-") as directory:
            root = Path(directory)
            (root / "src/intl").mkdir(parents=True)
            with contextlib.ExitStack() as stack:
                stack.enter_context(mock.patch.object(cloud, "ROOT", root))
                stack.enter_context(mock.patch.object(sys, "argv", ["check_cloud.py", "--profile", "core"]))
                stack.enter_context(mock.patch.object(cloud, "tracked_privacy", side_effect=lambda: events.append("privacy")))
                stack.enter_context(mock.patch.object(cloud, "tools", return_value=({}, "javac 21 fixture")))
                stack.enter_context(mock.patch.object(cloud, "compile_sources", side_effect=compile_fixture))
                stack.enter_context(mock.patch.object(cloud, "isolated_run", side_effect=run_fixture))
                stack.enter_context(mock.patch.object(cloud.subprocess, "check_output", return_value="fixture-head\n"))
                stack.enter_context(contextlib.redirect_stdout(io.StringIO()))
                self.assertEqual(cloud.main(), 0)

        self.assertEqual(events[:2], ["privacy", "compile"])
        runs = [event for event in events if isinstance(event, tuple)]
        for name in SAVE_CHECKS:
            self.assertEqual(runs.count((name, True)), 1, name + " did not run once in headless core")
        self.assertNotIn("SettingsDividerNativeProbe", cloud.CORE + cloud.SWING)

    def test_privacy_failure_stops_before_tools_compile_or_probe(self):
        with mock.patch.object(sys, "argv", ["check_cloud.py", "--profile", "core"]), \
                mock.patch.object(cloud, "tracked_privacy", side_effect=ValueError("synthetic privacy rejection")), \
                mock.patch.object(cloud, "tools") as tools, \
                mock.patch.object(cloud, "compile_sources") as compile_sources, \
                mock.patch.object(cloud, "isolated_run") as isolated_run:
            with self.assertRaisesRegex(ValueError, "privacy rejection"):
                cloud.main()
        tools.assert_not_called()
        compile_sources.assert_not_called()
        isolated_run.assert_not_called()

    def test_native_divider_probe_cannot_be_selected_by_cloud_cli(self):
        with mock.patch.object(sys, "argv", ["check_cloud.py", "--profile", "all", "--only", "SettingsDividerNativeProbe"]), \
                mock.patch.object(cloud, "tracked_privacy") as privacy, \
                contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit) as rejected:
                cloud.main()
        self.assertEqual(rejected.exception.code, 2)
        privacy.assert_not_called()


if __name__ == "__main__":
    unittest.main()
