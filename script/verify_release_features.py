#!/usr/bin/env python3
"""Run sequential release probes against the packaged JAR/runtime with isolated state."""
import argparse
from dataclasses import dataclass
import hashlib
import json
import math
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time
import zipfile

RETIRED_PROBES = {
    'AnnotationDeletionMouseProbe': 'Historical Control-wheel and Control-click deletion contract; use current gesture and owned-input probes',
    'TplsABEraseProbe': 'Historical A/B trial requires first right click to wait; replaced by immediate erasure/rollback and continuous-erasure probes',
    'TplsInputMappingProbe': 'Historical delayed-click cancellation and free-eraser toolbar contract; superseded by immediate-click, current gesture and preview probes',
}

PROBES = [
    'DoodleHypothesisKeyGestureProbe',
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
    'IdleSessionRecoveryProbe',
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

# These profiles replace the unbounded loops in build_and_run.sh. Stages in one
# contract share one fresh profile; independent probes never share user state.
BUILD_PROBES = [
    'LocaleShortcutProbe', 'ApplicationPathsProbe', 'AppearanceRenderingProbe',
    'BoxReasoningRenderingProbe', 'ChainRouteGeometryProbe', 'CompletionTransitionProbe',
    'SudokuReferenceParserProbe', 'MainLaunchArgumentsProbe', 'MacOSApplicationProbe',
    'NativeReasoningMatcherProbe', 'CurrentReasoningProbe', 'NativeReasoningLibraryProbe',
    'GroupedChainProbe', 'GeneralizedChainProbe', 'AlsManualChainProbe', 'ChainTextCodecProbe',
    'OptionsPersistenceProbe', 'PairedPaletteInvariantProbe', 'PuzzleHistoryProbe',
    'SessionStoreProbe', 'ReplayLongTextProbe', 'ReplayPersistenceProbe', 'SaveAlgorithmProbe',
    'SaveTraceProbe', 'DoodleCoordinatesProbe', 'SessionCompactCodecProbe',
    'SessionPrimitiveArraysProbe', 'SettingsPersistenceProbe', 'SessionPersistenceProbe',
    'SaveOptimizationHashProbe', 'OwnedProbeInputsProbe', 'ProbeInputContractProbe',
    'TemplateCheckScopeProbe', 'ToolbarIconRenderingProbe',
    'WindowLayoutProbe',
]
NATIVE_PROBES = [
    'GuiLocalizationProbe', 'ChainEdgeDeletionProbe', 'AnnotationDeletionProbe',
    'MissingCandidateDoubleClickProbe', 'MissingCandidateMouseProbe', 'ColoringInteractionProbe',
    'AnnotationInteractionProbe', 'AnnotationPerformanceProbe', 'SetAllSinglesInteractionProbe',
    'UserChainRenderingProbe', 'ReasoningInteractionProbe', 'CurrentReasoningInteractionProbe',
    'ToolbarChainStateProbe', 'TechniqueSelectorProbe', 'TechniqueSelectorNavigationProbe',
    'HintInteractionProbe', 'KeyboardHelpDialogProbe', 'PaletteStartupProbe',
    'SessionLifecycleProbe', 'TransientReferenceHighlightProbe', 'DoodleHypothesisSessionProbe',
    'OwnedProbeInputsNativeProbe',
]
SAVE_SMOKE_PROBES = [
    'SaveOptimizationHashProbe', 'SessionCompactCodecProbe', 'SessionPrimitiveArraysProbe',
    'SettingsDividerNativeProbe', 'SaveOptimizationLifecycleProbe',
]
PROBES += ['DoodleHypothesisSessionProbe', 'OwnedProbeInputsNativeProbe', *SAVE_SMOKE_PROBES]
PROFILES = {'release': PROBES, 'build': BUILD_PROBES,
            'native': NATIVE_PROBES + SAVE_SMOKE_PROBES, 'save-smoke': SAVE_SMOKE_PROBES}


@dataclass(frozen=True)
class Probe:
    platforms: tuple = ('darwin',)
    stages: tuple = (('main', ()),)
    timeout: float = 180
    class_name: str = ''
    needs_app: bool = False


CONTRACTS = {name: Probe() for names in PROFILES.values() for name in names}
for _name in ('ChainEvidenceNativeProbe', 'DoodleStrokeErasureNativeProbe',
              'DragFeedbackNativeProbe', 'DoodleDerivedInteractionProbe'):
    CONTRACTS[_name] = Probe()
for _name in BUILD_PROBES:
    if _name not in ('MacOSApplicationProbe', 'AppearanceRenderingProbe', 'ToolbarIconRenderingProbe'):
        CONTRACTS[_name] = Probe(platforms=('darwin', 'linux'))
CONTRACTS.update({
    'DoodleHypothesisSessionProbe': Probe(stages=(('seed', ('seed',)), ('verify', ('verify',)))),
    'SettingsDividerNativeProbe': Probe(stages=(('write', ('write', '{output}')), ('verify', ('verify', '{output}')))),
    'SaveOptimizationLifecycleProbe': Probe(stages=(('history', ('history-idle', '{output}', '20')), ('verify', ('verify', '{output}')))),
    'SaveOptimizationLightProbe': Probe(class_name='SaveOptimizationLifecycleProbe',
        stages=(('write', ('light', '{output}')), ('verify', ('verify', '{output}')))),
    'ReplaySharingProbe': Probe(needs_app=True, stages=(('main', ('{helper}', '{fixture}')),)),
})
for _name in ('ReplayRecoveryProbe', 'ReplayRecoveryGuiProbe', 'ReplayRecoveryAuditProbe', 'IdleSessionRecoveryProbe'):
    CONTRACTS[_name] = Probe(stages=(('main', ('{scenarios}',)),))


def _classpath_entries(classpath):
    return [Path(part).resolve() for part in classpath.split(os.pathsep)]


def _has_class(classpath, name):
    relative = 'sudoku/' + name + '.class'
    for entry in _classpath_entries(classpath):
        if entry.is_dir() and (entry / relative).is_file():
            return True
        if entry.is_file() and zipfile.is_zipfile(entry):
            with zipfile.ZipFile(entry) as archive:
                if relative in archive.namelist():
                    return True
    return False


def _locale_bundles(classpath):
    names = set()
    for entry in _classpath_entries(classpath):
        if entry.is_dir():
            paths = [p.name for p in (entry / 'intl').glob('*.properties')]
        elif entry.is_file() and zipfile.is_zipfile(entry):
            with zipfile.ZipFile(entry) as archive:
                paths = [p.split('/')[-1] for p in archive.namelist() if p.startswith('intl/') and p.endswith('.properties')]
        else:
            continue
        names.update(p[:-11] for p in paths if not p.endswith(('_de.properties', '_zh.properties')))
    if not names:
        raise ValueError('LocaleShortcutProbe requires intl/*.properties on the classpath')
    return tuple(sorted(names))


def _stop_group(process):
    # Only this runner's session is signalled. SIGTERM gives owned-input shutdown
    # hooks a chance to release keys before a hung VM needs SIGKILL.
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        pass
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        pass
    # A child can outlive its leader; always check the owned group as well.
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass
    process.wait()


def _execute(command, root, log_path, timeout):
    env = {key: value for key, value in os.environ.items()
           if key in ('USER', 'LOGNAME', 'SHELL', '__CF_USER_TEXT_ENCODING', 'DISPLAY', 'XAUTHORITY')}
    env.update(PATH='/usr/bin:/bin:/usr/sbin:/sbin', HOME=str(root / 'home'), TMPDIR=str(root / 'tmp'))
    # Recovery helpers create child JVMs. Quoted options preserve paths with spaces.
    env['JAVA_TOOL_OPTIONS'] = ' '.join('"-D' + key + '=' + str(root / folder) + '"'
        for key, folder in (('user.home', 'home'), ('java.io.tmpdir', 'tmp'), ('hodoku.data.dir', 'data')))
    start = time.monotonic()
    with log_path.open('w') as log:
        process = subprocess.Popen(command, env=env, cwd=root, stdout=log,
                                   stderr=subprocess.STDOUT, start_new_session=True)
        timed_out = False
        try:
            code = process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            timed_out = True
            _stop_group(process)
            code = process.returncode
        except BaseException:
            _stop_group(process)
            raise
    return {'exit_code': code, 'timeout': timed_out, 'pid': process.pid,
            'seconds': round(time.monotonic() - start, 2), 'log': str(log_path)}


def run_suite(*, java, classpath, report, names, app=None, timeout=None, ui_element=False, keep_going=False):
    """Run registered contracts only; return evidence and write results.json after each probe."""
    java, report = Path(java).resolve(), Path(report).resolve()
    app = Path(app).resolve() if app else None
    classpath = os.pathsep.join(str(p) for p in _classpath_entries(classpath))
    names = list(names)
    if not names:
        raise ValueError('Select at least one probe')
    if timeout is not None and (not math.isfinite(timeout) or timeout <= 0):
        raise ValueError('timeout must be positive')
    if not java.is_file() or not os.access(java, os.X_OK):
        raise ValueError('Java executable is unavailable: ' + str(java))
    # Validate every contract before any JVM starts, including retired selections.
    for name in names:
        if name in RETIRED_PROBES:
            raise ValueError('Retired probe ' + name + ': ' + RETIRED_PROBES[name])
        if name not in CONTRACTS:
            raise ValueError('Unregistered probe: ' + name)
        contract = CONTRACTS[name]
        if sys.platform not in contract.platforms:
            raise ValueError(name + ' requires platform ' + ', '.join(contract.platforms))
        if contract.needs_app and (app is None or not (app / 'Contents/MacOS/HoDoKuShare').is_file()):
            raise ValueError(name + ' requires --app with HoDoKuShare')
        if not _has_class(classpath, contract.class_name or name):
            raise ValueError('Missing compiled probe class: ' + (contract.class_name or name))
        if name == 'LocaleShortcutProbe':
            _locale_bundles(classpath)
    report.mkdir(parents=True, exist_ok=True)
    scratch = Path(tempfile.mkdtemp(prefix='profiles-', dir=report))
    results = []
    payload = {'schema': 2, 'passed': False, 'completed': 0, 'requested': names, 'results': results,
               'retired_probes': RETIRED_PROBES, 'isolated_profiles': str(scratch),
               'ui_element_for_test_jvms': ui_element,
               'scope': 'Registered sequential stages; fresh isolated state per probe; no real recipient sharing',
               'limits': 'Native probes require macOS desktop access. SIGKILL cannot run Java input cleanup hooks.'}
    if app:
        payload['jar_sha256'] = hashlib.sha256((app / 'Contents/app/Hodoku.jar').read_bytes()).hexdigest()
    def record():
        payload['completed'] = len(results)
        payload['passed'] = len(results) == len(names) and all(r['passed'] for r in results)
        (report / 'results.json').write_text(json.dumps(payload, indent=2) + '\n')
    record()
    for name in names:
        contract = CONTRACTS[name]
        root = scratch / name
        for folder in ('home', 'data', 'tmp', 'probe'):
            (root / folder).mkdir(parents=True, exist_ok=True)
        values = {'output': str(root / 'probe'), 'scenarios': str(root / 'scenarios'),
                  'helper': str(app / 'Contents/MacOS/HoDoKuShare') if app else '', 'fixture': str(root / 'probe.hrep')}
        if contract.needs_app:
            (root / 'probe.hrep').write_bytes(b'local helper cancellation probe')
        row = {'probe': name, 'passed': False, 'exit_code': -1, 'timeout': False, 'seconds': 0,
               'platforms': contract.platforms, 'profile': str(root), 'stages': []}
        results.append(row)
        for stage_name, arguments in contract.stages:
            arguments = [arg.format(**values) for arg in arguments]
            if name == 'LocaleShortcutProbe':
                arguments = list(_locale_bundles(classpath))
            command = [str(java), '-Xmx512m', '-Duser.language=zh', '-Duser.home=' + str(root / 'home'),
                       '-Dhodoku.data.dir=' + str(root / 'data'), '-Djava.io.tmpdir=' + str(root / 'tmp'),
                       '-Dhodoku.probe.output=' + str(root / 'probe')]
            if sys.platform == 'darwin':
                command.append('--add-exports=java.desktop/com.apple.laf=ALL-UNNAMED')
            if ui_element:
                command.append('-Dapple.awt.UIElement=true')
            command += ['-cp', classpath, 'sudoku.' + (contract.class_name or name), *arguments]
            suffix = '' if stage_name == 'main' else '-' + stage_name
            stage = {'stage': stage_name, 'arguments': arguments}
            row['stages'].append(stage)
            try:
                stage.update(_execute(command, root, report / (name + suffix + '.log'),
                                      timeout if timeout is not None else contract.timeout))
            except OSError as error:
                stage.update(exit_code=-1, timeout=False, seconds=0, error=str(error))
            row.update(exit_code=stage['exit_code'], timeout=stage['timeout'])
            row['seconds'] = round(row['seconds'] + stage['seconds'], 2)
            record()
            print(('PASS' if stage['exit_code'] == 0 and not stage['timeout'] else 'FAIL') + ': ' + name + '/' + stage_name, flush=True)
            if stage['exit_code'] != 0 or stage['timeout']:
                break
        row['passed'] = len(row['stages']) == len(contract.stages) and all(
            s['exit_code'] == 0 and not s['timeout'] for s in row['stages'])
        record()
        if not row['passed'] and not keep_going:
            break
    return payload


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument('--app', type=Path)
    source.add_argument('--java', type=Path, help='Build JDK Java; requires --classpath')
    parser.add_argument('--test-classes', type=Path, help='Required with --app')
    parser.add_argument('--classpath', help='Required with --java')
    parser.add_argument('--report-dir', type=Path, required=True)
    parser.add_argument('--profile', choices=PROFILES, default='release')
    parser.add_argument('--only', nargs='+', help='Registered probe names; retired names fail before launch')
    parser.add_argument('--timeout', type=float, help='Seconds per JVM stage, default from contract')
    parser.add_argument('--keep-going', action='store_true', help='Collect independent failures; never continue a failed stage sequence')
    parser.add_argument('--ui-element-probes', action='store_true', help='UIElement for test JVMs only')
    args = parser.parse_args()
    if args.app:
        if args.test_classes is None or args.classpath:
            parser.error('--app requires --test-classes and does not accept --classpath')
        java = args.app / 'Contents/runtime/Contents/Home/bin/java'
        classpath = os.pathsep.join((str(args.app.resolve() / 'Contents/app/Hodoku.jar'), str(args.test_classes.resolve())))
    else:
        if not args.classpath or args.test_classes:
            parser.error('--java requires --classpath and does not accept --test-classes')
        java, classpath = args.java, args.classpath
    try:
        result = run_suite(java=java, classpath=classpath, app=args.app, report=args.report_dir,
                           names=args.only or PROFILES[args.profile], timeout=args.timeout,
                           ui_element=args.ui_element_probes, keep_going=args.keep_going)
    except ValueError as error:
        parser.error(str(error))
    return 0 if result['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
