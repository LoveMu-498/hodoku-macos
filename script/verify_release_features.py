#!/usr/bin/env python3
"""Run sequential release probes against the packaged JAR/runtime with isolated state."""
import argparse
import json
import os
from pathlib import Path
import signal
import subprocess
import tempfile
import time

PROBES = [
    'ReplayCoreProbe', 'ReplayLifecycleProbe', 'ReplayProofProbe', 'ReplayAuthoredProbe',
    'ReplayEvidenceFailureProbe', 'ReplayRetentionProbe', 'ReplayRecoveryProbe',
    'ReplayRecoveryGuiProbe', 'ReplayRecoveryAuditProbe', 'ReplayRetentionNativeProbe',
    'ReplayViewerProbe', 'ReplayViewerEdgeProbe', 'ReplayBranchProbe',
    'ReplayInterchangeProbe', 'ReplayInterchangeGuiProbe', 'ReplayInterruptionPresentationProbe',
    'ReplayNativeInputProbe', 'ReplayMouseInteractionProbe', 'ReplayPlaybackHintProbe',
    'ReplayDialogMouseProbe', 'ReplaySharingProbe',
    'ChainTextCodecProbe', 'ChainOriginProbe', 'DoodleWheelProbe', 'AuthoredPreviewRenderingProbe',
    'AlsManualChainProbe', 'AlsManualChainTransactionProbe', 'GroupedChainProbe',
    'GroupedChainInteractionProbe', 'GroupedChainTransactionProbe', 'ChainEditingProbe',
    'AnnotationTimelineProbe', 'AnchoredDoodleProbe', 'PairedPaletteInvariantProbe',
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--app', type=Path, required=True)
    parser.add_argument('--test-classes', type=Path, required=True)
    parser.add_argument('--report-dir', type=Path, required=True)
    parser.add_argument('--only', nargs='+', choices=PROBES)
    args = parser.parse_args()
    app = args.app.resolve()
    report = args.report_dir.resolve()
    report.mkdir(parents=True, exist_ok=True)
    java = app / 'Contents/runtime/Contents/Home/bin/java'
    jar = app / 'Contents/app/Hodoku.jar'
    scratch = Path(tempfile.mkdtemp(prefix='hodoku-release-probes-'))
    env = {k:v for k,v in os.environ.items() if k in ('USER','LOGNAME','SHELL','__CF_USER_TEXT_ENCODING')}
    env['PATH'] = '/usr/bin:/bin:/usr/sbin:/sbin'
    results = []
    for name in (args.only or PROBES):
        root = scratch / name
        for folder in ('home','data','tmp'):
            (root / folder).mkdir(parents=True, exist_ok=True)
        env.update(HOME=str(root/'home'), TMPDIR=str(root/'tmp'))
        command = [str(java), '--add-exports=java.desktop/com.apple.laf=ALL-UNNAMED',
                   '-Duser.language=zh', '-Duser.home='+str(root/'home'),
                   '-Dhodoku.data.dir='+str(root/'data'), '-Djava.io.tmpdir='+str(root/'tmp'),
                   '-cp', str(jar)+os.pathsep+str(args.test_classes.resolve()), 'sudoku.'+name]
        if name in ('ReplayRecoveryProbe','ReplayRecoveryGuiProbe','ReplayRecoveryAuditProbe'):
            command.append(str(root/'scenarios'))
        if name == 'ReplaySharingProbe':
            # This helper boundary checks ready/cancel only and never sends a file.
            fixture = root/'probe.hrep'
            fixture.write_bytes(b'local helper cancellation probe')
            command.extend([str(app/'Contents/MacOS/HoDoKuShare'),str(fixture)])
        start = time.monotonic()
        with (report/(name+'.log')).open('w') as log:
            process = subprocess.Popen(command, env=env, cwd=root, stdout=log,
                                       stderr=subprocess.STDOUT, start_new_session=True)
            timed_out = False
            try:
                code = process.wait(timeout=180)
            except subprocess.TimeoutExpired:
                timed_out = True
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait()
                code = -1
        result = {'probe':name, 'exit_code':code, 'timeout':timed_out,
                  'seconds':round(time.monotonic()-start,2)}
        results.append(result)
        (report/'results.json').write_text(json.dumps({'passed':all(r['exit_code']==0 for r in results),
                'completed':len(results), 'results':results,
                'scope':'Packaged JAR and bundled Java; isolated local state; no real recipient sharing',
                'limits':'Not full-platform testing or real sleep; clipboard-mutating probes not run here'},indent=2)+'\n')
        print(('PASS' if code==0 else 'FAIL')+': '+name,flush=True)
        if code != 0:
            return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
