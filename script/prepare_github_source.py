#!/usr/bin/env python3
"""Prepare a source-only review candidate. Never commit, push, or rewrite history."""
import argparse
from datetime import datetime, timezone
import hashlib
import io
import json
from pathlib import Path
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parents[1]
PATHS = ('src', 'test', 'script', 'docs/distribution',
         'docs/open-source-release.md', 'docs/releases', 'docs/replay-sharing.md', 'docs/replay-file-format.md', 'README.md', 'COPYING',
         'UPSTREAM.md', 'CHANGES.md', 'THIRD_PARTY_NOTICES.md', '.gitignore')
EXCLUDED = {'__pycache__', '.DS_Store'}


def source_files(root):
    files = []
    for name in PATHS:
        item = root / name
        if not item.exists():
            raise ValueError('Missing required source material: ' + name)
        candidates = item.rglob('*') if item.is_dir() else [item]
        for path in candidates:
            relative = path.relative_to(root)
            if any(part in EXCLUDED for part in relative.parts) or path.suffix == '.pyc':
                continue
            if path.is_symlink():
                raise ValueError('Review symlink before public export: ' + str(relative))
            if not path.is_file():
                continue
            if path.name.startswith('.env') or path.suffix in ('.hcfg', '.hrep', '.checkpoint', '.pem', '.key', '.p12', '.pfx', '.jar', '.zip', '.dmg'):
                raise ValueError('Unexpected private/binary file in source selection: ' + str(relative))
            if path.stat().st_size > 50 * 1024 * 1024:
                raise ValueError('Unexpected large file in source selection: ' + str(relative))
            files.append(path)
    return sorted(set(files))


def public_tar_metadata(info):
    """Do not embed the local account name, uid or private timestamps in source archives."""
    info.uid = info.gid = 0
    info.uname = info.gname = ''
    info.mtime = 0
    info.pax_headers = {}
    return info


def export(root, output):
    if output.exists():
        raise ValueError('Choose a new output directory; existing files are retained')
    files = source_files(root)
    from audit_public_source import audit
    findings = audit(files, root)
    if findings:
        for name, number, kind in findings:
            print(f'{name}:{number}: {kind} [value redacted]')
        raise ValueError('Public content scan failed; sensitive values were not printed')
    output.mkdir(parents=True)
    manifest = {'kind': 'source-only review candidate, not a published release',
                'created_utc': datetime.now(timezone.utc).isoformat(),
                'base_commit': subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip(),
                'snapshot': 'current working tree including selected uncommitted and untracked source files',
                'files': {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in files}}
    archive_path = output / 'HoDoKu-source.tar.gz'
    with tarfile.open(archive_path, 'w:gz') as archive:
        for path in files:
            archive.add(path, arcname=str(path.relative_to(root)), recursive=False, filter=public_tar_metadata)
        instructions = root / 'docs/distribution/PUBLIC_AGENTS.md'
        if instructions.is_file():
            archive.add(instructions, arcname='AGENTS.md', recursive=False, filter=public_tar_metadata)
            manifest['files']['AGENTS.md'] = hashlib.sha256(instructions.read_bytes()).hexdigest()
        content = (json.dumps(manifest, indent=2) + '\n').encode()
        info = tarfile.TarInfo('SOURCE_SNAPSHOT.json')
        info.size = len(content)
        archive.addfile(info, io.BytesIO(content))
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    (output / 'SHA256SUMS.txt').write_text(hashlib.sha256(archive_path.read_bytes()).hexdigest() + '  HoDoKu-source.tar.gz\n')
    (output / 'REVIEW.txt').write_text('''Source-only review candidate; no repository was created or uploaded.
Includes the current selected source tree, not merely the base Git commit.
Excludes Java binaries/sources, app/DMG outputs, installation/build backups,
.scratch, user state and Git history. This file selection is not a full secrets
or provenance audit. Review public files before publication. See docs/open-source-release.md.
''')
    print(f'Prepared {len(files)} source files: {archive_path}')
    return archive_path


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    destination = args.output or ROOT / ('dist/github-source-' + datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    export(ROOT, destination.resolve())
