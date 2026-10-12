"""Failure-injection checks for the timing gate; mocks are not native probe evidence."""
import contextlib
import importlib.util
import io
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

SCRIPT_DIRECTORY = Path(__file__).resolve().parents[2] / 'script'
# The command-line script imports its sibling runner through this same directory.
sys.path.insert(0, str(SCRIPT_DIRECTORY))
try:
    spec = importlib.util.spec_from_file_location('compare_save_lifecycle',
                                                  SCRIPT_DIRECTORY / 'compare_save_lifecycle.py')
    compare = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(compare)
finally:
    sys.path.pop(0)


class PreflightTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='hodoku-preflight-unit-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        for name in ('baseline', 'candidate'):
            app = self.root / name
            jar = app / 'Contents/app/Hodoku.jar'
            jar.parent.mkdir(parents=True)
            jar.write_bytes(('unit-test-only-' + name).encode())
            java = app / 'Contents/runtime/Contents/Home/bin/java'
            java.parent.mkdir(parents=True)
            java.write_text('#!/bin/sh\nexit 99\n')
            java.chmod(0o700)
        (self.root / 'classes').mkdir()
        observer = self.root / 'observer'
        observer.write_text('#!/bin/sh\nexit 99\n')
        observer.chmod(0o700)

    def arguments(self, output='result'):
        return ['--baseline', str(self.root / 'baseline'),
                '--candidate', str(self.root / 'candidate'),
                '--test-classes', str(self.root / 'classes'),
                '--observer', str(self.root / 'observer'),
                '--output', str(self.root / output), '--cases', 'light', '--count', '1']

    def report(self, output='result'):
        return json.loads((self.root / output / 'comparison.json').read_text())

    def invoke(self, arguments):
        with contextlib.redirect_stdout(io.StringIO()):
            return compare.main(arguments)

    def assert_blocked(self, result, stage):
        self.assertFalse(result['preflight']['passed'])
        self.assertEqual(result['preflight']['failed_stage'], stage)
        self.assertEqual(result['samples'], [])
        self.assertTrue(all(not values for values in result['medians_ms'].values()))

    def test_summary_failure_blocks_all_native_probes_and_matrix(self):
        with mock.patch.object(compare, 'run_summary_preflight', return_value={'passed': False, 'exit_code': 1}), \
             mock.patch.object(compare.release_features, 'run_suite', create=True) as suite, \
             mock.patch.object(compare, 'run_matrix') as matrix:
            self.assertEqual(self.invoke(self.arguments()), 1)
        suite.assert_not_called()
        matrix.assert_not_called()
        result = self.report()
        self.assert_blocked(result, 'summary')
        self.assertEqual([row['name'] for row in result['preflight']['stages']], ['summary'])

    def test_each_candidate_probe_failure_blocks_baseline_and_matrix(self):
        for index, name in enumerate(compare.CANDIDATE_PREFLIGHT):
            with self.subTest(probe=name):
                evidence = {'passed': False, 'results': [
                    {'probe': probe, 'exit_code': 1 if probe == name else 0}
                    for probe in compare.CANDIDATE_PREFLIGHT[:index + 1]]}
                with mock.patch.object(compare, 'run_summary_preflight', return_value={'passed': True}), \
                     mock.patch.object(compare.release_features, 'run_suite', create=True, return_value=evidence) as suite, \
                     mock.patch.object(compare, 'run_matrix') as matrix:
                    self.assertEqual(self.invoke(self.arguments(name)), 1)
                matrix.assert_not_called()
                suite.assert_called_once()
                self.assertEqual(suite.call_args.kwargs['names'], compare.CANDIDATE_PREFLIGHT)
                self.assertEqual(suite.call_args.kwargs['app'], self.root / 'candidate')
                self.assert_blocked(self.report(name), 'candidate')

    def test_candidate_runner_exception_is_recorded_and_stops_matrix(self):
        with mock.patch.object(compare, 'run_summary_preflight', return_value={'passed': True}), \
             mock.patch.object(compare.release_features, 'run_suite', create=True,
                               side_effect=OSError('injected isolated runner failure')) as suite, \
             mock.patch.object(compare, 'run_matrix') as matrix:
            self.assertEqual(self.invoke(self.arguments()), 1)
        suite.assert_called_once()
        matrix.assert_not_called()
        result = self.report()
        self.assert_blocked(result, 'candidate')
        self.assertIn('OSError', result['preflight']['stages'][-1]['error'])

    def test_baseline_light_failure_blocks_matrix_after_candidate_pass(self):
        outcomes = [{'passed': True, 'results': []},
                    {'passed': False, 'results': [{'probe': 'SaveOptimizationLightProbe', 'exit_code': 1}]}]
        with mock.patch.object(compare, 'run_summary_preflight', return_value={'passed': True}), \
             mock.patch.object(compare.release_features, 'run_suite', create=True, side_effect=outcomes) as suite, \
             mock.patch.object(compare, 'run_matrix') as matrix:
            self.assertEqual(self.invoke(self.arguments()), 1)
        self.assertEqual(suite.call_count, 2)
        self.assertEqual(suite.call_args_list[0].kwargs['names'], compare.CANDIDATE_PREFLIGHT)
        self.assertEqual(suite.call_args_list[1].kwargs['names'], ['SaveOptimizationLightProbe'])
        self.assertEqual(suite.call_args_list[1].kwargs['app'], self.root / 'baseline')
        matrix.assert_not_called()
        self.assert_blocked(self.report(), 'baseline')

    def test_success_runs_ordered_gates_before_matrix_and_excludes_gate_timings(self):
        order = []
        def summary(*unused):
            order.append('summary')
            return {'passed': True, 'seconds': 900}
        def suite(**arguments):
            name = arguments['app'].name
            order.append(name)
            self.assertEqual(arguments['ui_element'], False)
            self.assertEqual(arguments['timeout'], 120)
            self.assertEqual(arguments['report'], self.root / 'result/preflight' / name)
            return {'passed': True, 'results': [{'seconds': 800}]}
        def matrix(*unused):
            order.append('matrix')
        with mock.patch.object(compare, 'run_summary_preflight', side_effect=summary), \
             mock.patch.object(compare.release_features, 'run_suite', create=True, side_effect=suite), \
             mock.patch.object(compare, 'run_matrix', side_effect=matrix):
            self.assertEqual(self.invoke(self.arguments()), 0)
        self.assertEqual(order, ['summary', 'candidate', 'baseline', 'matrix'])
        result = self.report()
        self.assertTrue(result['preflight']['passed'])
        self.assertEqual(result['samples'], [])
        self.assertEqual(result['medians_ms'], {'light': {}})

    def test_summary_timeout_is_a_failed_gate(self):
        with mock.patch.object(compare.subprocess, 'run', side_effect=subprocess.TimeoutExpired('summary', 1)):
            result = compare.run_summary_preflight(self.root / 'summary-timeout', 1)
        self.assertFalse(result['passed'])
        self.assertTrue(result['timeout'])
        self.assertTrue(Path(result['log']).is_file())

    def test_invalid_inputs_fail_before_preflight_or_output_creation(self):
        invalid = [ ['--count', '0'], ['--timeout', '0'], ['--cases', 'unknown'],
                    ['--cases', 'verify'], ['--cases', 'history:9'],
                    ['--cases', 'history-idle:101'], ['--cases', 'cold:20'],
                    ['--cases', 'cold', 'cold'], ['--candidate-only-cases', 'cold'],
                    ['--observer', str(self.root / 'missing-observer')],
                    ['--baseline', str(self.root / 'missing-app')],
                    ['--test-classes', str(self.root / 'missing-classes')] ]
        with mock.patch.object(compare, 'run_preflight') as preflight:
            for extra in invalid:
                with self.subTest(arguments=extra), contextlib.redirect_stderr(io.StringIO()):
                    with self.assertRaises(SystemExit) as failure:
                        self.invoke(self.arguments() + extra)
                    self.assertEqual(failure.exception.code, 2)
                    self.assertFalse((self.root / 'result').exists())
        preflight.assert_not_called()

    def test_no_skip_preflight_switch(self):
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as failure:
            self.invoke(self.arguments() + ['--skip-preflight'])
        self.assertEqual(failure.exception.code, 2)
        self.assertFalse((self.root / 'result').exists())


if __name__ == '__main__':
    unittest.main()
