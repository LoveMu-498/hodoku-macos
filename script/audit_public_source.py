#!/usr/bin/env python3
"""Scan selected public files; print locations/categories only, never secret values."""
import re
from pathlib import Path

PATTERNS = {
    'provider-token': re.compile(r'\b(?:sk-(?:proj-|ant-)?[A-Za-z0-9_-]{20,}|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[A-Z0-9]{16}|AIza[A-Za-z0-9_-]{30,}|xox[baprs]-[A-Za-z0-9-]{15,})'),
    'private-key': re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----'),
    'credential-assignment': re.compile(r'''(?i)(?:api[_-]?key|access[_-]?token|client[_-]?secret|password)\s*[=:]\s*["']([A-Za-z0-9_+/.=-]{16,})["']'''),
    'credential-url': re.compile(r'https?://[^/\s:@]+:[^/\s@]+@'),
    'personal-path': re.compile(r'/Users/(?!Shared/)[A-Za-z0-9._-]+/|/home/[a-zA-Z0-9_-]+/|[A-Z]:\\Users\\[A-Za-z0-9._-]+\\'),
}
PRIVATE_NAMES = {'.env', '.netrc', '.npmrc', '.pypirc', 'credentials.json', 'hodoku.hcfg', 'last-session.xml'}


def audit(files, root):
    findings = []
    for path in files:
        name = str(path.relative_to(root))
        if path.name in PRIVATE_NAMES or path.name.startswith('.env.') or path.suffix in ('.pem', '.key', '.p12', '.pfx'):
            findings.append((name, 0, 'private-file'))
        try:
            content = path.read_text()
        except UnicodeError:
            continue
        for number, line in enumerate(content.splitlines(), 1):
            for kind, pattern in PATTERNS.items():
                if pattern.search(line):
                    findings.append((name, number, kind))
    return findings


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    args = parser.parse_args()
    root = args.directory.resolve()
    files = [p for p in root.rglob('*') if p.is_file() and '.git' not in p.relative_to(root).parts
             and '__pycache__' not in p.parts]
    findings = audit(files, root)
    for name, number, kind in findings:
        print(f'{name}:{number}: {kind} [value redacted]')
    print(f'{"FAIL" if findings else "PASS"}: public-content scan, {len(files)} files, {len(findings)} findings')
    raise SystemExit(1 if findings else 0)
