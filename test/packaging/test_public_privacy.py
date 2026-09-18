import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('audit_public_source', ROOT / 'script/audit_public_source.py')
scanner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scanner)


class PrivacyScanTest(unittest.TestCase):
    def scan(self, value, name='example.txt'):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            path = root / name
            path.write_text(value)
            return scanner.audit([path], root)

    def test_provider_tokens_and_private_keys(self):
        for value in ('sk-' + 'a' * 40, 'ghp_' + 'b' * 36,
                      '-----BEGIN ' + 'PRIVATE KEY-----'):
            findings = self.scan(value)
            self.assertTrue(findings)
            self.assertNotIn(value, str(findings))

    def test_generic_credentials(self):
        self.assertTrue(self.scan('api_key=' + '"' + 'aB9dE1fG2hI3jK4lM5n' + '"'))

    def test_personal_paths_and_files(self):
        self.assertTrue(self.scan('/Users/' + 'private-user/' + 'records'))
        self.assertTrue(self.scan('configuration', '.env'))

    def test_generic_path_patterns_are_not_personal(self):
        self.assertFalse(self.scan(r'/Users/|/opt/|/usr/local/'))
        self.assertFalse(self.scan('GPL-3.0-or-later'))


if __name__ == '__main__':
    unittest.main()
