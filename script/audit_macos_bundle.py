#!/usr/bin/env python3
"""Fail closed on non-portable Mach-O links, architecture and bundle resources."""
import argparse
import json
import os
from pathlib import Path
import plistlib
import re
import subprocess
import sys
import zipfile

MAGIC = {bytes.fromhex(x) for x in ('feedface', 'cefaedfe', 'feedfacf', 'cffaedfe',
                                    'cafebabe', 'bebafeca', 'cafebabf', 'bfbafeca')}
SYSTEM = ('/usr/lib/', '/System/Library/')
LOADS = {'LC_LOAD_DYLIB', 'LC_LOAD_WEAK_DYLIB', 'LC_REEXPORT_DYLIB', 'LC_LOAD_UPWARD_DYLIB'}


def run(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT)


def inspect(path, arch):
    output = run('/usr/bin/otool', '-arch', arch, '-l', str(path))
    deps, rpaths, minimum = [], [], '0'
    for block in re.split(r'Load command \d+\n', output)[1:]:
        cmd = re.search(r'\bcmd (\S+)', block)
        if not cmd:
            continue
        kind = cmd.group(1)
        if kind in LOADS:
            deps.append(re.search(r'\bname (.*?) \(offset', block).group(1))
        elif kind == 'LC_RPATH':
            rpaths.append(re.search(r'\bpath (.*?) \(offset', block).group(1))
        elif kind in ('LC_BUILD_VERSION', 'LC_VERSION_MIN_MACOSX'):
            match = re.search(r'\b(?:minos|version) ([\d.]+)', block)
            if match:
                minimum = match.group(1)
    return {'dependencies': deps, 'rpaths': rpaths, 'minimum_macos': minimum}


def version(value):
    return tuple((list(map(int, value.split('.'))) + [0, 0])[:3])


def audit(root, arch, runtime_only=False):
    root = root.resolve()
    errors, binaries = [], {}
    def error(message):
        errors.append(message)
    def inside(path):
        return path.resolve().is_relative_to(root)
    for parent, dirs, files in os.walk(root):
        for name in dirs + files:
            p = Path(parent) / name
            if p.is_symlink() and (not p.exists() or not inside(p)):
                error(f'Broken or external symlink: {p.relative_to(root)}')
        for name in files:
            p = Path(parent) / name
            if p.is_symlink() or not p.is_file():
                continue
            with p.open('rb') as stream:
                if stream.read(4) not in MAGIC:
                    continue
            arches = run('/usr/bin/lipo', '-archs', str(p)).strip().split()
            if arch not in arches:
                error(f'Wrong architecture: {p.relative_to(root)}: {arches}')
                continue
            binaries[p] = inspect(p, arch)
    if not binaries:
        error('No Mach-O binaries found')
    executables = [p for p in binaries if p.parent.name in ('bin', 'MacOS')]
    def expand(value, owner, executable):
        value = value.replace('@loader_path', str(owner.parent))
        value = value.replace('@executable_path', str(executable.parent))
        return Path(value)
    # For dynamically loaded JVM modules the launcher/libjli/libjvm supply
    # inherited run paths. Validate those paths too; never search Homebrew.
    providers = [p for p in binaries if p.name in ('libjli.dylib', 'libjvm.dylib')]
    for p, info in binaries.items():
        for rp in info['rpaths']:
            if rp.startswith('/') and not rp.startswith(SYSTEM):
                error(f'Non-portable RPATH: {p.relative_to(root)} -> {rp}')
            elif not rp.startswith(('@loader_path', '@executable_path', '/')):
                error(f'Unsupported RPATH: {p.relative_to(root)} -> {rp}')
            elif rp.startswith('@') and not any(inside(expand(rp, p, exe)) for exe in executables):
                error(f'Escaping RPATH: {p.relative_to(root)} -> {rp}')
        for dep in info['dependencies']:
            if dep.startswith(SYSTEM):
                continue
            if dep.startswith('/'):
                error(f'External dependency: {p.relative_to(root)} -> {dep}')
                continue
            candidates = []
            for exe in executables:
                if dep.startswith('@rpath/'):
                    for provider in [p, exe] + providers:
                        for rp in binaries[provider]['rpaths']:
                            candidates.append(expand(rp, provider, exe) / dep[len('@rpath/'):])
                elif dep.startswith(('@loader_path/', '@executable_path/')):
                    candidates.append(expand(dep, p, exe))
            if not any(inside(c) and c.is_file() and c.resolve() in binaries for c in candidates):
                error(f'Unresolved bundled dependency: {p.relative_to(root)} -> {dep}')
    minimum = max((i['minimum_macos'] for i in binaries.values()), key=version, default='0')
    if not runtime_only:
        required = ['Contents/Info.plist', 'Contents/MacOS/HoDoKu',
                    'Contents/app/HoDoKu.cfg', 'Contents/app/Hodoku.jar',
                    'Contents/runtime/Contents/Home/lib/modules',
                    'Contents/runtime/Contents/Home/legal/java.base/LICENSE',
                    'Contents/runtime/Contents/Home/bin/java', 'Contents/MacOS/HoDoKuShare']
        for item in required:
            if not (root / item).is_file():
                error(f'Missing bundle resource: {item}')
        plist_path = root / 'Contents/Info.plist'
        if plist_path.exists():
            with plist_path.open('rb') as stream:
                plist = plistlib.load(stream)
            declared = plist.get('LSMinimumSystemVersion', '0')
            if version(declared) < version(minimum):
                error(f'Info.plist minimum {declared} is below binary minimum {minimum}')
            if re.search(r'/Users/|/opt/|/usr/local/|JAVA_HOME|JDK_HOME', json.dumps(plist)):
                error('Machine-specific bundle configuration: Contents/Info.plist')
        for item in ('Contents/MacOS/HoDoKu', 'Contents/runtime/Contents/Home/bin/java', 'Contents/MacOS/HoDoKuShare'):
            if not os.access(root / item, os.X_OK):
                error(f'Not executable: {item}')
        for item in ('Contents/app/HoDoKu.cfg', 'Contents/app/.jpackage.xml'):
            p = root / item
            if p.exists() and re.search(r'/Users/|/opt/|/usr/local/|JAVA_HOME|JDK_HOME', p.read_text()):
                error(f'Machine-specific launcher configuration: {item}')
        for name in ('hodoku.hcfg', 'last-session.xml'):
            if list(root.rglob(name)):
                error(f'Personal state must not be distributed: {name}')
        jar = root / 'Contents/app/Hodoku.jar'
        if jar.exists():
            with zipfile.ZipFile(jar) as z:
                for item in ('sudoku/Main.class', 'templates.dat', 'intl/MainFrame_zh.properties',
                             'img/hodoku02-256.png', 'help/keyboard_zh.html', 'COPYING'):
                    if item not in z.namelist():
                        error(f'Missing JAR resource: {item}')
        try:
            run('/usr/bin/codesign', '--verify', '--strict', str(root / 'Contents/MacOS/HoDoKuShare'))
            run('/usr/bin/codesign', '--verify', '--deep', '--strict', str(root))
        except subprocess.CalledProcessError as ex:
            error('Signature verification failed: ' + ex.output.strip())
    return {'passed': not errors, 'architecture': arch, 'minimum_macos': minimum,
            'mach_o_count': len(binaries), 'errors': errors,
            'binaries': {str(p.relative_to(root)): info for p, info in binaries.items()}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('path', type=Path)
    parser.add_argument('--arch', default='arm64', choices=['arm64', 'x86_64'])
    parser.add_argument('--runtime-only', action='store_true')
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    result = audit(args.path, args.arch, args.runtime_only)
    if args.report:
        args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(f"{'PASS' if result['passed'] else 'FAIL'}: {result['mach_o_count']} Mach-O files, "
          f"{result['architecture']}, minimum macOS {result['minimum_macos']}")
    for error in result['errors']:
        print(error, file=sys.stderr)
    if not result['passed']:
        print('Use a self-contained macOS JDK: HODOKU_JAVA_HOME="$(bash script/prepare_distribution_jdk.sh)"',
              file=sys.stderr)
    return 0 if result['passed'] else 1


if __name__ == '__main__':
    sys.exit(main())
