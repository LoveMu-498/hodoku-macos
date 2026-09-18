import copy
import importlib.util
import json
from pathlib import Path
import tarfile
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


def load(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'script' / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


java = load('prepare_java_sources')
source = load('prepare_github_source')


class JavaMaterialsTest(unittest.TestCase):
    def setUp(self):
        self.binary = {'scmRef': 'jdk-21.0.12.1+1_adopt',
                       'buildRef': 'https://github.com/adoptium/temurin-build/commit/' + java.BUILD_COMMIT,
                       'version': {'version': '21.0.12.1+1-LTS'},
                       'sha256': java.BINARY_SHA, 'os': 'mac', 'arch': 'aarch64'}
        self.source = copy.deepcopy(self.binary)
        self.source['sha256'] = java.SOURCE_SHA

    def test_source_must_match_binary_scm_and_build(self):
        java.verify_metadata(self.binary, self.source)
        for field in ('scmRef', 'buildRef'):
            with self.subTest(field=field):
                changed = copy.deepcopy(self.source)
                changed[field] = 'different-source'
                with self.assertRaises(ValueError):
                    java.verify_metadata(self.binary, changed)

    def test_wrong_source_digest_and_platform_rejected(self):
        changed = copy.deepcopy(self.source)
        changed['sha256'] = '0' * 64
        with self.assertRaises(ValueError):
            java.verify_metadata(self.binary, changed)
        changed = copy.deepcopy(self.binary)
        changed['arch'] = 'x64'
        with self.assertRaises(ValueError):
            java.verify_metadata(changed, self.source)

    def test_empty_source_archive_cannot_pass(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'empty.tar.gz'
            with tarfile.open(path, 'w:gz'):
                pass
            with self.assertRaises(ValueError):
                java.verify_source(path)


class PublicSourceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        directories = {'src', 'test', 'script', 'docs/adr', 'docs/distribution'}
        for name in source.PATHS:
            path = self.root / name
            if name in directories:
                path.mkdir(parents=True, exist_ok=True)
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('fixture')
        (self.root / 'src/Main.java').write_text('class Main {}')

    def test_runtime_backups_and_history_excluded(self):
        for name in ('build/jdk/java', 'dist/HoDoKu.app/file', 'installation-backups/private',
                     '.scratch/private.txt', '.git/config'):
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('must not export')
        selected = {str(p.relative_to(self.root)) for p in source.source_files(self.root)}
        self.assertIn('src/Main.java', selected)
        self.assertFalse(any(p.startswith(('build/', 'dist/', 'installation-backups/', '.scratch/', '.git/'))
                             for p in selected))

    def test_private_file_in_selected_source_rejected(self):
        (self.root / 'src/.env').write_text('not a real credential')
        with self.assertRaises(ValueError):
            source.source_files(self.root)

    def test_symlink_in_selected_source_rejected(self):
        (self.root / 'src/external').symlink_to('/tmp')
        with self.assertRaises(ValueError):
            source.source_files(self.root)


if __name__ == '__main__':
    unittest.main()
