#!/usr/bin/env python3
"""Run sequential release probes against the packaged JAR/runtime with isolated state."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import signal
import subprocess
import tempfile
import time

RETIRED_PROBES = {
    'TplsABEraseProbe': 'Historical A/B trial requires first right click to wait; replaced by immediate erasure/rollback and continuous-erasure probes',
    'TplsInputMappingProbe': 'Historical delayed-click cancellation and free-eraser toolbar contract; superseded by immediate-click, current gesture and preview probes',
}

PROBES = [
    'TplsImmediateCoverageProbe',
    'ImmediateMappedClickProbe',
    'AnnotationDispatchDeadlineProbe',
    'TplsGestureRefinementProbe',
    'ContinuousEraserWheelProbe',
    'DoodleFeedbackPolishProbe',
    'ProjectedDeletionAndBoxPriorityProbe',
    'ChainBlockedPreviewProbe',
    'DoodleCommandPolarityProbe',
    'DoodleHypothesisCompositionProbe',
    'DoodleCompositionApplyProbe',
    'DoodleLegacyStateProbe',
    'DoodleHypothesisInputRenderingProbe',
    'DoodleOrthogonalUiProbe',
    'DoodleStartFourStateProbe',
    'DoodlePFocusPreviewProbe',
    'DoodleEnterNativeProbe',
    'GlobalSinglesShortcutProbe',
    'BoxSelectionToggleProbe',
    'BoxSingleReasoningProbe',
    'GeneralizedChainProbe',
    'GeneralizedChainInteractionProbe',
    'CompletePathMatchProbe',
    'TabExactPathInteractionProbe',
    'EmptyTechniqueKeyForwardProbe',
    'ChainPreviewEscapeProbe',
    'ChainPreviewSourceChangeProbe',
    'ChainTailBacktrackProbe',
    'ChainTailBacktrackNativeProbe',
    'ChainEndpointCueProbe',
    'CandidateFilterGroupProbe',
    'ReplayCoreProbe',
    'ReplayLifecycleProbe',
    'ReplayProofProbe',
    'ReplayAuthoredProbe',
    'ReplayEvidenceFailureProbe',
    'ReplayRetentionProbe',
    'ReplayRecoveryProbe',
    'ReplayRecoveryGuiProbe',
    'ReplayRecoveryAuditProbe',
    'ReplayRetentionNativeProbe',
    'ReplayRetirementProbe',
    'ReplayViewerProbe',
    'ReplayViewerEdgeProbe',
    'ReplayBranchProbe',
    'ReplayInterchangeProbe',
    'ReplayInterchangeGuiProbe',
    'ReplayInterruptionPresentationProbe',
    'ReplayNativeInputProbe',
    'ReplayMouseInteractionProbe',
    'ReplayPlaybackHintProbe',
    'ReplayDialogMouseProbe',
    'ReplaySharingProbe',
    'ReplayEscapeProbe',
    'ChainTextCodecProbe',
    'DoodleWheelProbe',
    'AuthoredPreviewRenderingProbe',
    'AlsManualChainProbe',
    'AlsManualChainTransactionProbe',
    'ChainEditingProbe',
    'AnnotationTimelineProbe',
    'AnchoredDoodleProbe',
    'PairedPaletteInvariantProbe',
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--app', type=Path, required=True)
    parser.add_argument('--test-classes', type=Path, required=True)
    parser.add_argument('--report-dir', type=Path, required=True)
    parser.add_argument('--only', nargs='+', choices=PROBES)
    parser.add_argument('--keep-going', action='store_true', help='Collect independent probe failures before returning nonzero')
    parser.add_argument('--ui-element-probes', action='store_true', help='Set UIElement for test JVMs only; never changes the delivered launcher')
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
                   '-Dhodoku.probe.output='+str(root/'tmp'),
                   '-cp', str(jar)+os.pathsep+str(args.test_classes.resolve()), 'sudoku.'+name]
        if args.ui_element_probes:
            command.insert(1, '-Dapple.awt.UIElement=true')
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
                'completed':len(results), 'results':results, 'retired_probes':RETIRED_PROBES,
                'jar_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(), 'ui_element_for_test_jvms':args.ui_element_probes,
                'scope':'Packaged JAR and bundled Java; isolated local state; no real recipient sharing',
                'limits':'Not full-platform testing or real sleep; clipboard-mutating probes not run here'},indent=2)+'\n')
        print(('PASS' if code==0 else 'FAIL')+': '+name,flush=True)
        if code != 0 and not args.keep_going:
            return 1
    return 0 if all(r['exit_code']==0 for r in results) else 1


if __name__ == '__main__':
    raise SystemExit(main())
