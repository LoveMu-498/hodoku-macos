#!/usr/bin/env python3
"""Exercise the scanner against real Mach-O fixtures, including broken packages."""
import importlib.util
from pathlib import Path
import subprocess
import shutil
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('bundle_audit', ROOT / 'script/audit_macos_bundle.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class BundleAuditTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='hodoku-audit-fixture-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'bin').mkdir()
        (self.root / 'lib').mkdir()
        (self.root / 'lib.c').write_text('int answer(void) { return 42; }\n')
        (self.root / 'main.c').write_text('extern int answer(void); int main(void) { return answer() == 42 ? 0 : 1; }\n')
        self.lib = self.root / 'lib/libanswer.dylib'
        self.exe = self.root / 'bin/check'
        self.command('clang', '-arch', 'arm64', '-dynamiclib', str(self.root / 'lib.c'),
                     '-Wl,-install_name,@rpath/libanswer.dylib', '-o', str(self.lib))
        self.command('clang', '-arch', 'arm64', str(self.root / 'main.c'), str(self.lib),
                     '-Wl,-rpath,@loader_path/../lib', '-o', str(self.exe))

    def command(self, *args):
        subprocess.run(args, check=True, capture_output=True)

    def test_self_contained_rpath_and_relocation(self):
        with tempfile.TemporaryDirectory(prefix='hodoku-relocation-') as destination:
            relocated = Path(destination) / '中文 分享目录'
            shutil.copytree(self.root, relocated)
            self.command(str(relocated / 'bin/check'))
            self.assertTrue(module.audit(relocated, 'arm64', True)['passed'])

    def test_external_dependency_is_rejected(self):
        self.command('install_name_tool', '-change', '@rpath/libanswer.dylib',
                     '/opt/homebrew/opt/example/lib/libanswer.dylib', str(self.exe))
        result = module.audit(self.root, 'arm64', True)
        self.assertFalse(result['passed'])
        self.assertTrue(any('External dependency' in e for e in result['errors']))

    def test_missing_relative_dependency_is_rejected(self):
        self.command('install_name_tool', '-change', '@rpath/libanswer.dylib',
                     '@loader_path/missing.dylib', str(self.exe))
        result = module.audit(self.root, 'arm64', True)
        self.assertFalse(result['passed'])
        self.assertTrue(any('Unresolved bundled dependency' in e for e in result['errors']))

    def test_external_symlink_is_rejected(self):
        (self.root / 'escape').symlink_to('/usr/lib')
        result = module.audit(self.root, 'arm64', True)
        self.assertFalse(result['passed'])
        self.assertTrue(any('external symlink' in e for e in result['errors']))

    def test_missing_sharing_helper_is_rejected(self):
        result = module.audit(self.root, 'arm64', False)
        self.assertFalse(result['passed'])
        self.assertIn('Missing bundle resource: Contents/MacOS/HoDoKuShare', result['errors'])
        self.assertIn('Not executable: Contents/MacOS/HoDoKuShare', result['errors'])

    def test_wrong_architecture_is_rejected(self):
        self.command('clang', '-arch', 'x86_64', '-dynamiclib', str(self.root / 'lib.c'),
                     '-o', str(self.root / 'lib/intel-only.dylib'))
        result = module.audit(self.root, 'arm64', True)
        self.assertFalse(result['passed'])
        self.assertTrue(any('Wrong architecture' in e for e in result['errors']))


if __name__ == '__main__':
    unittest.main()
