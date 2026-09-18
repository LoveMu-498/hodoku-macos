#!/usr/bin/env python3
"""Exercise the relocated, packaged launcher with isolated data and a clean PATH."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('app', type=Path)
parser.add_argument('--report-dir', type=Path, required=True)
args = parser.parse_args()
app = args.app.resolve()
report = args.report_dir.resolve()
report.mkdir(parents=True, exist_ok=True)
# Retain diagnostics and isolated state; never write to the real user profile.
sandbox = Path(tempfile.mkdtemp(prefix='hodoku-share-check-'))
for name in ('home', 'data', 'tmp'):
    (sandbox / name).mkdir()
env = {key: value for key, value in os.environ.items()
       if key in ('USER', 'LOGNAME', 'SHELL', '__CF_USER_TEXT_ENCODING')}
env.update(PATH='/usr/bin:/bin:/usr/sbin:/sbin', HOME=str(sandbox / 'home'),
           TMPDIR=str(sandbox / 'tmp'))
env['JAVA_TOOL_OPTIONS'] = ' '.join([
    '-Duser.home=' + str(sandbox / 'home'),
    '-Dhodoku.data.dir=' + str(sandbox / 'data'),
    '-Djava.io.tmpdir=' + str(sandbox / 'tmp'),
    '-Xlog:library=info:file=' + str(report / 'loaded-libraries.log')])
exe = str(app / 'Contents/MacOS/HoDoKu')

def cli(name, arguments, expected):
    result = subprocess.run([exe] + arguments, env=env, cwd=sandbox,
                            capture_output=True, text=True, timeout=60)
    (report / (name + '.stdout')).write_text(result.stdout)
    (report / (name + '.stderr')).write_text(result.stderr)
    if result.returncode != 0 or expected not in result.stdout:
        raise RuntimeError(name + ' failed; see ' + str(report))

cli('help', ['/?'], 'Usage: java -Xmx512m -jar hodoku.jar')
cli('solve', ['530070000600195000098000060800060003400803001700020006060000280000419005000080079',
              '/o', 'stdout'], '0 puzzles not solved logically!')

# Launch the actual app executable twice, through native initialization and Quit.
for attempt in (1, 2):
    log = sandbox / 'tmp/hodoku.log'
    if log.exists():
        log.rename(sandbox / ('startup-previous-' + str(attempt) + '.log'))
    with (report / ('gui-' + str(attempt) + '.log')).open('w') as output:
        process = subprocess.Popen([exe], cwd=sandbox, env=env, stdout=output, stderr=output)
        try:
            ready = False
            for _ in range(120):
                if process.poll() is not None:
                    raise RuntimeError('GUI exited during startup')
                if log.exists() and 'CONFIG: laf=Mac OS X' in log.read_text(errors='replace'):
                    ready = True
                    break
                time.sleep(.25)
            if not ready:
                raise RuntimeError('Native Aqua startup did not finish')
            time.sleep(2)
            if process.poll() is not None:
                raise RuntimeError('GUI exited after native initialization')
            # Send native Quit by PID, so an installed copy with the same bundle
            # identifier can never receive this test's Quit event.
            quit_script = '''use framework "AppKit"
on run argv
    set targetApp to current application's NSRunningApplication's runningApplicationWithProcessIdentifier:((item 1 of argv) as integer)
    if targetApp is missing value then error "Test process not found"
    if not (targetApp's terminate() as boolean) then error "Native Quit was rejected"
end run'''
            subprocess.run(['/usr/bin/osascript', '-e', quit_script, str(process.pid)],
                           check=True, capture_output=True, timeout=20)
            if process.wait(timeout=20) != 0:
                raise RuntimeError('Native Quit returned nonzero')
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
    (report / ('startup-' + str(attempt) + '.log')).write_text(log.read_text())
    for name in ('hodoku.hcfg', 'last-session.xml'):
        if not (sandbox / 'data' / name).is_file():
            raise RuntimeError('Native Quit failed to save ' + name)
loaded = (report / 'loaded-libraries.log').read_text()
if 'libfontmanager.dylib' not in loaded or 'libawt_lwawt.dylib' not in loaded:
    raise RuntimeError('No evidence of font manager and native AWT loading')
if '/opt/homebrew/' in loaded or '/usr/local/' in loaded:
    raise RuntimeError('Runtime loaded an external package-manager library')
(report / 'runtime-verification.json').write_text(json.dumps({
    'passed': True, 'macos': subprocess.check_output(['sw_vers', '-productVersion'], text=True).strip(),
    'architecture': os.uname().machine, 'isolated_state': str(sandbox),
    'checks': ['packaged launcher help', 'CLI solve', 'Aqua and font library startup',
               'native Quit persistence', 'relaunch', 'clean PATH without external Java'],
    'limits': ['Not an M1 or older macOS hardware test', 'Not Gatekeeper notarization validation']
}, indent=2) + '\n')
print('PASS: packaged launcher, solving, fonts/Aqua, native Quit and relaunch')
