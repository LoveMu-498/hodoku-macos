"""Exercise runner failure boundaries with real subprocesses, without native input."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('probe_runner', ROOT / 'script/verify_release_features.py')
runner = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = runner
spec.loader.exec_module(runner)


class ProbeRunnerTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='probe contract space ')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.classes = self.root / 'classes'
        (self.classes / 'sudoku').mkdir(parents=True)
        for name in runner.CONTRACTS:
            (self.classes / 'sudoku' / ((runner.CONTRACTS[name].class_name or name) + '.class')).touch()
        self.java = self.root / 'java'
        self.platform = mock.patch.object(runner.sys, 'platform', 'darwin')
        self.platform.start()
        self.addCleanup(self.platform.stop)

    def executable(self, body):
        self.java.write_text('#!' + sys.executable + '\n' + body)
        self.java.chmod(0o755)

    def run_probe(self, names, **kwargs):
        return runner.run_suite(java=self.java, classpath=str(self.classes), report=self.root / 'report',
                                names=names, **kwargs)

    def test_two_jvms_share_isolated_profile_and_required_stages(self):
        self.executable('''import json, os, sys
from pathlib import Path
mode=sys.argv[-1]
root=Path.cwd()
assert os.environ['HOME']==str(root/'home')
assert os.environ['TMPDIR']==str(root/'tmp')
assert '-Dhodoku.data.dir='+str(root/'data') in sys.argv
assert 'JAVA_TOOL_OPTIONS' in os.environ
assert 'HODOKU_OBSERVER_READY' not in os.environ
if mode=='seed': (root/'data/state').write_text(str(os.getpid()))
elif mode=='verify': assert int((root/'data/state').read_text()) != os.getpid()
else: raise AssertionError('missing required seed/verify argument')
''')
        result = self.run_probe(['DoodleHypothesisSessionProbe'])
        self.assertTrue(result['passed'])
        stages = result['results'][0]['stages']
        self.assertEqual([s['arguments'] for s in stages], [['seed'], ['verify']])
        self.assertNotEqual(stages[0]['pid'], stages[1]['pid'])

    def test_failed_write_never_runs_verify_even_with_keep_going(self):
        self.executable('''import sys
from pathlib import Path
if sys.argv[-1]=='seed': sys.exit(7)
if sys.argv[-1]=='verify': raise AssertionError('verify must not run')
''')
        result = self.run_probe(['DoodleHypothesisSessionProbe', 'SaveOptimizationHashProbe'], keep_going=True)
        self.assertFalse(result['passed'])
        self.assertEqual(result['completed'], 2)
        self.assertEqual(len(result['results'][0]['stages']), 1)
        self.assertEqual(result['results'][0]['exit_code'], 7)
        self.assertTrue(result['results'][1]['passed'])

    def test_retired_or_unsupported_probe_rejected_before_any_launch(self):
        self.executable('raise AssertionError("must never run")\n')
        with mock.patch.object(runner.subprocess, 'Popen') as launch:
            with self.assertRaisesRegex(ValueError, 'Retired probe AnnotationDeletionMouseProbe'):
                self.run_probe(['SaveOptimizationHashProbe', 'AnnotationDeletionMouseProbe'])
            with mock.patch.object(runner.sys, 'platform', 'linux'):
                with self.assertRaisesRegex(ValueError, 'requires platform'):
                    self.run_probe(['SettingsDividerNativeProbe'])
            with self.assertRaisesRegex(ValueError, 'Unregistered'):
                self.run_probe(['TypoProbe'])
            launch.assert_not_called()

    def test_missing_required_resource_fails_before_launch(self):
        self.executable('raise AssertionError("must never run")\n')
        with mock.patch.object(runner.subprocess, 'Popen') as launch:
            with self.assertRaisesRegex(ValueError, 'requires --app'):
                self.run_probe(['ReplaySharingProbe'])
            with self.assertRaisesRegex(ValueError, 'requires intl'):
                self.run_probe(['LocaleShortcutProbe'])
            (self.classes / 'sudoku/SaveOptimizationHashProbe.class').unlink()
            with self.assertRaisesRegex(ValueError, 'Missing compiled probe'):
                self.run_probe(['SaveOptimizationHashProbe'])
            launch.assert_not_called()

    def test_nonfinite_or_nonpositive_timeout_rejected_before_launch(self):
        self.executable('raise AssertionError("must never run")\n')
        with mock.patch.object(runner.subprocess, 'Popen') as launch:
            for value in (float('inf'), float('-inf'), float('nan'), 0, -1):
                with self.subTest(timeout=value), self.assertRaisesRegex(ValueError, 'timeout must be positive'):
                    self.run_probe(['SaveOptimizationHashProbe'], timeout=value)
            launch.assert_not_called()

    def test_timeout_is_failure_even_if_term_handler_exits_zero(self):
        self.executable('''import signal, sys, time
from pathlib import Path
def stop(*args):
    Path('cleanup').write_text('owned cleanup ran')
    sys.exit(0)
signal.signal(signal.SIGTERM, stop)
while True: time.sleep(.01)
''')
        result = self.run_probe(['DoodleHypothesisSessionProbe', 'SaveOptimizationHashProbe'], timeout=.5)
        self.assertFalse(result['passed'])
        self.assertEqual(result['completed'], 1)
        row = result['results'][0]
        self.assertTrue(row['timeout'])
        self.assertEqual(len(row['stages']), 1)
        self.assertEqual(row['exit_code'], 0)
        self.assertTrue((Path(row['profile']) / 'cleanup').is_file())
        with self.assertRaises(ProcessLookupError):
            os.kill(row['stages'][0]['pid'], 0)
        saved = json.loads((self.root / 'report/results.json').read_text())
        self.assertFalse(saved['passed'])

    def test_same_probe_in_later_suite_gets_fresh_state(self):
        self.executable('''from pathlib import Path
assert not Path('data/state').exists()
Path('data/state').write_text('created')
''')
        first = self.run_probe(['SaveOptimizationHashProbe'])
        second = self.run_probe(['SaveOptimizationHashProbe'])
        self.assertTrue(first['passed'] and second['passed'])
        self.assertNotEqual(first['isolated_profiles'], second['isolated_profiles'])

    def test_timeout_terminates_owned_child_and_leaves_other_process_alone(self):
        self.executable('''import signal, subprocess, sys, time
from pathlib import Path
child=subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(30)'])
Path('child.pid').write_text(str(child.pid))
def stop(*args):
    child.wait(timeout=2)
    sys.exit(0)
signal.signal(signal.SIGTERM, stop)
while True: time.sleep(.01)
''')
        other = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(30)'], start_new_session=True)
        try:
            result = self.run_probe(['SaveOptimizationHashProbe'], timeout=.5)
            self.assertFalse(result['passed'])
            row = result['results'][0]
            self.assertTrue(row['timeout'])
            child = int((Path(row['profile']) / 'child.pid').read_text())
            with self.assertRaises(ProcessLookupError):
                os.kill(child, 0)
            self.assertIsNone(other.poll())
        finally:
            other.terminate()
            other.wait(timeout=2)


if __name__ == '__main__':
    unittest.main()
