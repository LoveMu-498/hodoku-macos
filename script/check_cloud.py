#!/usr/bin/env python3
"""Build source and run bounded cloud regressions; never package, install or publish."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import tempfile
import time
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CORE = (
    'ApplicationPathsProbe', 'CompletionTransitionProbe', 'SudokuReferenceParserProbe',
    'MainLaunchArgumentsProbe', 'NativeReasoningMatcherProbe',
    'CurrentReasoningProbe', 'NativeReasoningLibraryProbe', 'GroupedChainProbe',
    'GeneralizedChainProbe', 'AlsManualChainProbe', 'ChainTextCodecProbe',
    'OptionsPersistenceProbe', 'PairedPaletteInvariantProbe', 'PuzzleHistoryProbe',
    'SessionStoreProbe', 'WindowLayoutProbe', 'ReplayCoreProbe', 'ReplayRecoveryProbe',
    'ReplayRetirementProbe', 'ReplayInterchangeProbe',
)
SWING = (
    'ImmediateMappedClickProbe', 'DoodleCompositionApplyProbe',
    'DoodleCommandPolarityProbe', 'DoodlePFocusPreviewProbe',
    'ChainPreviewSourceChangeProbe', 'ChainTailBacktrackProbe',
    'BoxSingleReasoningProbe', 'BoxSelectionToggleProbe',
    'ReplayLifecycleProbe', 'ReplayViewerProbe', 'ReplayInterchangeGuiProbe',
    'SessionLifecycleProbe', 'AppearanceRenderingProbe',
    'ToolbarIconRenderingProbe', 'KeyboardHelpDialogProbe',
    'ChainRouteGeometryProbe', 'BoxReasoningRenderingProbe',
    'ReplayProofProbe', 'ReplayAuthoredProbe', 'ReplayRecoveryAuditProbe',
)
# MacOSApplicationProbe registers native Desktop handlers; ReplaySharingProbe needs
# the compiled macOS share helper. They remain in the macOS build/release workflows.


def tracked_privacy():
    # Include visible new files, but never inspect ignored build outputs or .git credentials.
    from audit_public_source import audit
    names = subprocess.check_output(
        ['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=ROOT)
    files = []
    for name in sorted(set(os.fsdecode(p) for p in names.split(b'\0') if p)):
        path = ROOT / name
        if path.is_symlink():
            raise ValueError('Review source symlink before upload: ' + name)
        if path.is_file():
            files.append(path)
    findings = audit(files, ROOT)
    for name, number, kind in findings:
        print(f'{name}:{number}: {kind} [value redacted]', flush=True)
    if findings:
        raise ValueError('Public content scan failed; values were not printed')
    print(f'PASS public-content patterns: {len(files)} selected files', flush=True)


def tools():
    selected = {}
    for name in ('java', 'javac', 'jar'):
        home = os.environ.get('JAVA_HOME')
        path = Path(home) / 'bin' / name if home else None
        selected[name] = str(path) if path and path.is_file() else shutil.which(name)
        if not selected[name]:
            raise ValueError('Missing JDK tool: ' + name + '; run bash script/setup_cloud.sh')
    version = subprocess.check_output([selected['javac'], '-version'], stderr=subprocess.STDOUT, text=True).strip()
    if not re.search(r'\bjavac 21(?:\.|\s|$)', version):
        raise ValueError('Cloud checks require JDK 21; found ' + version)
    return selected, version


def compile_sources(jdk, folder):
    classes, tests = folder / 'classes', folder / 'test-classes'
    classes.mkdir()
    tests.mkdir()
    for source_root, destination, extra in (
            (ROOT / 'src', classes, []),
            (ROOT / 'test', tests, ['-cp', os.pathsep.join((str(classes), str(ROOT / 'src')))])):
        paths = sorted(source_root.rglob('*.java'))
        arguments = folder / (destination.name + '.args')
        arguments.write_text('\n'.join('"' + str(p).replace('\\', '\\\\').replace('"', '\\"') + '"' for p in paths) + '\n')
        subprocess.run([jdk['javac'], '--release', '8', '-encoding', 'UTF-8',
                        *extra, '-d', str(destination), '@' + str(arguments)], cwd=folder, check=True)
    for resource in ('intl', 'img', 'help'):
        shutil.copytree(ROOT / 'src' / resource, classes / resource)
    for resource in ('version.properties', 'templates.dat'):
        shutil.copy2(ROOT / 'src' / resource, classes / resource)
    shutil.copy2(ROOT / 'COPYING', classes / 'COPYING')
    jar = folder / 'Hodoku.jar'
    subprocess.run([jdk['jar'], '--create', '--file', str(jar), '--main-class', 'sudoku.Main',
                    '-C', str(classes), '.'], check=True)
    with zipfile.ZipFile(jar) as archive:
        for resource in ('sudoku/Main.class', 'version.properties', 'templates.dat', 'COPYING',
                         'intl/MainFrame_zh.properties', 'help/keyboard_zh.html', 'img/hodoku02-256.png'):
            if resource not in archive.namelist():
                raise ValueError('Missing JAR resource: ' + resource)
    return jar, tests


def isolated_run(command, headless, jdk, report, name, timeout):
    started = time.monotonic()
    with tempfile.TemporaryDirectory(prefix='hodoku-cloud-') as temporary:
        sandbox = Path(temporary)
        for child in ('home', 'data', 'tmp'):
            (sandbox / child).mkdir()
        env = {key: value for key, value in os.environ.items()
               if key in ('PATH', 'DISPLAY', 'XAUTHORITY', 'LANG', 'LC_ALL', 'SYSTEMROOT')}
        env['HOME'] = str(sandbox / 'home')
        env['TMPDIR'] = str(sandbox / 'tmp')
        # Recovery probes launch children; keep their Java home/tmp properties isolated too.
        env['JAVA_TOOL_OPTIONS'] = ('-Duser.home=' + str(sandbox / 'home') +
                                    ' -Djava.io.tmpdir=' + str(sandbox / 'tmp'))
        if name in ('ReplayRecoveryProbe', 'ReplayRecoveryAuditProbe'):
            command = [*command, str(sandbox / 'recovery')]
        java = [jdk['java'], '-Xmx512m', '-Djava.awt.headless=' + str(headless).lower(),
                '-Dhodoku.data.dir=' + str(sandbox / 'data'), '-Duser.home=' + str(sandbox / 'home'),
                '-Djava.io.tmpdir=' + str(sandbox / 'tmp'), '-Duser.language=en', '-Duser.country=US',
                *command]
        # Kill only this isolated probe's process group on timeout, never other applications.
        with (report / (name + '.log')).open('w') as log:
            process = subprocess.Popen(java, cwd=sandbox, env=env, stdout=log, stderr=subprocess.STDOUT,
                                       start_new_session=True)
            timed_out = False
            try:
                code = process.wait(timeout=timeout)
            except subprocess.TimeoutExpired:
                timed_out = True
                os.killpg(process.pid, signal.SIGKILL)
                code = process.wait()
    result = {'name': name, 'exit_code': code, 'timeout': timed_out,
              'seconds': round(time.monotonic() - started, 2)}
    print(f'{"PASS" if code == 0 and not timed_out else "FAIL"} {name} ({result["seconds"]}s)', flush=True)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile', choices=('core', 'swing', 'all'), default='core')
    parser.add_argument('--only', nargs='+', choices=CORE + SWING, help='Run selected probes from this profile')
    parser.add_argument('--timeout', type=int, default=90, help='Seconds per isolated Java process')
    args = parser.parse_args()
    if args.timeout < 1:
        parser.error('--timeout must be positive')
    available = (CORE if args.profile == 'core' else SWING if args.profile == 'swing' else CORE + SWING)
    if args.only and any(name not in available for name in args.only):
        parser.error('--only must belong to the selected profile')
    if args.profile in ('swing', 'all') and os.uname().sysname == 'Linux' and not os.environ.get('DISPLAY'):
        parser.error('Swing checks need a display: xvfb-run -a python3 script/check_cloud.py --profile ' + args.profile)
    tracked_privacy()
    jdk, version = tools()
    folder = ROOT / 'build' / 'cloud' / (datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S-') + uuid.uuid4().hex[:8])
    folder.mkdir(parents=True)
    report = folder / 'reports'
    report.mkdir()
    jar, tests = compile_sources(jdk, folder)
    classpath = os.pathsep.join((str(jar), str(tests)))
    results = [isolated_run(['-jar', str(jar), '/h'], True, jdk, report, 'JarHelp', args.timeout)]
    if results[-1]['exit_code'] == 0 and 'Usage: java' not in (report / 'JarHelp.log').read_text():
        results[-1]['exit_code'] = 1
        print('FAIL JAR help did not reach the application entry point', flush=True)
    bundles = sorted(p.stem for p in (ROOT / 'src/intl').glob('*.properties') if not p.stem.endswith(('_de', '_zh')))
    results.append(isolated_run(['-cp', classpath, 'sudoku.LocaleShortcutProbe', *bundles], True,
                                jdk, report, 'LocaleShortcutProbe', args.timeout))
    for name in (args.only or available):
        results.append(isolated_run(['-cp', classpath, 'sudoku.' + name], name in CORE,
                                    jdk, report, name, args.timeout))
    manifest = {'profile': args.profile, 'jdk': version, 'system': os.uname().sysname,
                'commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
                'dirty': bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT)),
                'jar_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(), 'results': results,
                'scope': 'source JAR and selected Linux/cloud regressions; not macOS packaging acceptance'}
    (report / 'results.json').write_text(json.dumps(manifest, indent=2) + '\n')
    failures = [r['name'] for r in results if r['exit_code'] != 0 or r['timeout']]
    print(f'{len(results) - len(failures)}/{len(results)} checks passed; reports: {report.relative_to(ROOT)}', flush=True)
    if failures:
        print('Failed: ' + ', '.join(failures) + '; read the corresponding local report logs', flush=True)
    return 1 if failures else 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (ValueError, subprocess.CalledProcessError) as error:
        print('FAIL: ' + str(error), flush=True)
        raise SystemExit(1)
