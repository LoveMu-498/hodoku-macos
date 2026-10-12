#!/usr/bin/env python3
"""Alternating real GUI closes, isolated profiles, exact readback, external PID exit evidence."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import statistics
import subprocess
import sys
import time

import verify_release_features as release_features


CANDIDATE_PREFLIGHT = [
    'SaveOptimizationHashProbe',
    'SessionCompactCodecProbe',
    'SessionPrimitiveArraysProbe',
    'SettingsDividerNativeProbe',
    'SaveOptimizationLifecycleProbe',
]
BASELINE_PREFLIGHT = ['SaveOptimizationLightProbe']
MODES = {'light', 'light-idle', 'cold', 'idle', 'edit-after-idle',
         'settings-only', 'completed', 'history', 'history-idle'}


def validate_inputs(parser, args):
    """Reject unusable inputs before creating reports or launching an application."""
    if args.timeout <= 0:
        parser.error('--timeout must be positive')
    if len(args.cases) != len(set(args.cases)):
        parser.error('--cases must not contain duplicates')
    for case in args.cases:
        mode, separator, layers = case.partition(':')
        if mode not in MODES:
            parser.error('unknown case: ' + case)
        if separator and (mode not in {'history', 'history-idle'}
                          or not layers.isdecimal() or not 10 <= int(layers) <= 100):
            parser.error('history cases alone accept a layer count, from 10 through 100: ' + case)
    if not set(args.candidate_only_cases).issubset(args.cases):
        parser.error('--candidate-only-cases must be selected in --cases')
    if args.output.exists():
        parser.error('--output must be a new directory: ' + str(args.output))
    for name in ('baseline', 'candidate'):
        app = getattr(args, name)
        java = app / 'Contents/runtime/Contents/Home/bin/java'
        jar = app / 'Contents/app/Hodoku.jar'
        if not jar.is_file() or not java.is_file() or not os.access(java, os.X_OK):
            parser.error('--' + name + ' must contain Hodoku.jar and an executable bundled Java: ' + str(app))
    if not args.test_classes.is_dir():
        parser.error('--test-classes must be a directory: ' + str(args.test_classes))
    if not args.observer.is_file() or not os.access(args.observer, os.X_OK):
        parser.error('--observer must be an executable file: ' + str(args.observer))


def run_summary_preflight(report, timeout):
    report.mkdir(parents=True, exist_ok=True)
    log_path = report / 'summary-test.log'
    test = Path(__file__).resolve().parents[1] / 'test/telemetry/test_save_trace_summary.py'
    started = time.monotonic()
    with log_path.open('w') as log:
        try:
            process = subprocess.run([sys.executable, str(test)], stdout=log,
                                     stderr=subprocess.STDOUT, timeout=timeout)
            return {'passed': process.returncode == 0, 'exit_code': process.returncode,
                    'timeout': False, 'seconds': time.monotonic() - started, 'log': str(log_path)}
        except subprocess.TimeoutExpired:
            return {'passed': False, 'exit_code': None, 'timeout': True,
                    'seconds': time.monotonic() - started, 'log': str(log_path)}


def run_preflight(args, apps, base, result, record):
    """Functional evidence gates repeated timing; these runs never become samples."""
    preflight = result['preflight'] = {'passed': False, 'stages': []}
    for name, probes in [('summary', None), ('candidate', CANDIDATE_PREFLIGHT),
                         ('baseline', BASELINE_PREFLIGHT)]:
        report = base / 'preflight' / name
        stage = {'name': name, 'passed': False, 'status': 'running', 'report': str(report)}
        if probes is not None:
            stage['probes'] = list(probes)
        preflight['stages'].append(stage)
        record()
        print('PREFLIGHT', name, flush=True)
        try:
            if probes is None:
                evidence = run_summary_preflight(report, args.timeout)
            else:
                app = apps[name]
                evidence = release_features.run_suite(
                    java=app / 'Contents/runtime/Contents/Home/bin/java',
                    classpath=str(app / 'Contents/app/Hodoku.jar') + os.pathsep + str(args.test_classes.resolve()),
                    report=report, names=list(probes), app=app,
                    timeout=args.timeout, ui_element=False)
            stage['evidence'] = evidence
            stage['passed'] = evidence.get('passed') is True
        except Exception as error:
            stage['error'] = type(error).__name__ + ': ' + str(error)
        stage['status'] = 'passed' if stage['passed'] else 'failed'
        if not stage['passed']:
            preflight['failed_stage'] = name
            record()
            print('FAIL preflight ' + name + '; performance matrix was not started', flush=True)
            return False
        record()
    preflight['passed'] = True
    record()
    return True


def run_matrix(args, apps, base, reports, record):
    baseline_memory_failures = set()
    for case in args.cases:
        mode, _, layers = case.partition(':')
        for repeat in range(1,args.count+1):
            versions = ['baseline','candidate'] if repeat%2 else ['candidate','baseline']
            if case in args.candidate_only_cases: versions = ['candidate']
            for version in versions:
                if version == 'baseline' and case in baseline_memory_failures:
                    print('SKIP repeated baseline memory failure', case, repeat, flush=True)
                    continue
                root=base/f'{case.replace(":","-")}-{repeat}-{version}'
                for part in ['home','data','tmp','probe']: (root/part).mkdir(parents=True)
                app=apps[version]; java=app/'Contents/runtime/Contents/Home/bin/java'; jar=app/'Contents/app/Hodoku.jar'
                command=[str(java),'--add-exports=java.desktop/com.apple.laf=ALL-UNNAMED','-Xmx512m','-Duser.language=zh',
                         '-Duser.home='+str(root/'home'),'-Dhodoku.data.dir='+str(root/'data'),'-Djava.io.tmpdir='+str(root/'tmp'),
                         '-cp',str(jar)+':'+str(args.test_classes.resolve()),'sudoku.SaveOptimizationLifecycleProbe']
                ready=root/'observer-ready'; environment=dict(os.environ,HODOKU_OBSERVER_READY=str(ready))
                row={'case':case,'repeat':repeat,'version':version,'load_start':os.getloadavg(),'verified':False}
                print('START',root.name,flush=True)
                with (root/'app.log').open('w') as output:
                    process=subprocess.Popen(command+[mode,str(root/'probe')]+([layers] if layers else []),stdout=output,stderr=subprocess.STDOUT,env=environment)
                    observer=subprocess.Popen([str(args.observer.resolve()),str(process.pid),str(root/'external.jsonl'),str(ready)],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
                    row['pid']=process.pid
                    deadline=time.monotonic()+args.timeout
                    memory_seen=None
                    while process.poll() is None and time.monotonic()<deadline:
                        time.sleep(.25)
                        # Bound inspection and stop a known failed fixture before repeated
                        # allocation failures can burden the user's desktop.
                        with (root/'app.log').open('rb') as recent:
                            recent.seek(max(0,(root/'app.log').stat().st_size-65536))
                            if b'OutOfMemoryError' in recent.read():
                                memory_seen=memory_seen or time.monotonic();row['memory_failure']=True
                        if memory_seen is not None and time.monotonic()-memory_seen>=2:
                            row['memory_failure']=True;break
                    if process.poll() is None:
                        if not row.get('memory_failure'):row['timeout_seconds']=args.timeout
                        # This handle was created above for this isolated fixture only.
                        process.terminate()
                        try:process.wait(timeout=5)
                        except subprocess.TimeoutExpired:process.kill();process.wait()
                    row['returncode']=process.returncode
                    if row.get('memory_failure') and version=='baseline':baseline_memory_failures.add(case)
                    observer_output,_=observer.communicate(timeout=15)
                    row['observer_returncode']=observer.returncode;(root/'observer.log').write_text(observer_output)
                row['load_end']=os.getloadavg()
                log=(root/'app.log').read_text(errors='replace')
                close=re.search(r'CLOSE_START wall_epoch_ms=(\d+)',log)
                rows=[json.loads(line) for line in (root/'external.jsonl').read_text().splitlines()]
                ended=next((r for r in rows if r['phase']=='process.exit'),None)
                if close and ended and not row.get('timeout_seconds') and not row.get('memory_failure'):
                    row['dispatch_to_exit_ms']=ended['wall_epoch_ms']-int(close.group(1))
                if row['returncode']==0 and row['observer_returncode']==0 and close and ended and not row.get('memory_failure'):
                    with (root/'readback.log').open('w') as output:
                        verify=subprocess.run(command+['verify',str(root/'probe')],stdout=output,stderr=subprocess.STDOUT,timeout=args.timeout)
                    row['verified']=verify.returncode==0
                if (root/'data/last-session.xml').exists(): row['session_bytes']=(root/'data/last-session.xml').stat().st_size
                if (root/'data/hodoku.hcfg').exists(): row['settings_bytes']=(root/'data/hodoku.hcfg').stat().st_size
                diagnostics=root/'data/diagnostics'
                if diagnostics.exists():
                    command_summary=['python3',str(Path(__file__).with_name('summarize_save_trace.py')),str(diagnostics),'--external',str(root/'external.jsonl'),'--output',str(root/'summary.json')]
                    with (root/'summary.log').open('w') as output: subprocess.run(command_summary,stdout=output,stderr=subprocess.STDOUT)
                row['path']=str(root);reports.append(row);record()
                print('DONE',root.name,'verified='+str(row['verified']),str(row.get('dispatch_to_exit_ms','unavailable'))+' ms',flush=True)
                if not row['verified'] and version=='candidate': raise SystemExit('Candidate failed; inspect '+str(root))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--candidate', type=Path, required=True)
    parser.add_argument('--test-classes', type=Path, required=True)
    parser.add_argument('--observer', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--count', type=int, default=3, choices=range(1, 11))
    parser.add_argument('--cases', nargs='+', default=['cold', 'idle', 'edit-after-idle', 'settings-only', 'completed', 'history:20', 'history-idle:20'])
    parser.add_argument('--timeout', type=int, default=120)
    parser.add_argument('--candidate-only-cases', nargs='*', default=[],
                        help='Explicitly unpaired cases; for bounded testing after a retained baseline failure')
    args = parser.parse_args(argv)
    validate_inputs(parser, args)
    base = args.output.resolve(); base.mkdir(parents=True, exist_ok=False)
    apps = {name: getattr(args, name).resolve() for name in ['baseline', 'candidate']}
    reports = []
    result = {'schema': 1, 'heap': '512m', 'samples': reports,
              'apps': {name: {'path': str(app), 'jar_sha256': hashlib.sha256((app/'Contents/app/Hodoku.jar').read_bytes()).hexdigest()} for name,app in apps.items()},
              'boundary': 'CLOSE_START stdout immediately before WINDOW_CLOSING to external kernel NOTE_EXIT receipt; includes dispatch and <=1ms fixture quantization',
              'scope': 'Same probe bytecode, fixed fixtures, alternating fresh JVMs. Baseline is the prior verified artifact; unrelated intervening appearance changes remain in candidate.'}
    result['candidate_only_cases'] = args.candidate_only_cases
    summary = base/'comparison.json'
    def record():
        result['medians_ms'] = {}
        for case in args.cases:
            result['medians_ms'][case] = {}
            for version in apps:
                values = [row['dispatch_to_exit_ms'] for row in reports if row['case']==case and row['version']==version and row.get('verified')]
                if values: result['medians_ms'][case][version] = statistics.median(values)
        summary.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    if not run_preflight(args, apps, base, result, record):
        print(summary, flush=True)
        return 1
    run_matrix(args, apps, base, reports, record)
    print(summary,flush=True)
    return 0


if __name__=='__main__':
    raise SystemExit(main())
