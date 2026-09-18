#!/usr/bin/env python3
"""Assemble pinned Temurin sources and build materials outside Git history."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / 'build/third-party-sources/temurin-21.0.12.1'
RELEASE = 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/'
BUILD_COMMIT = 'e6ba7dec3d07654074559310376a3ae89da5f4ac'
BINARY_SHA = '3623232f33a9c3baadf304480b2535f9a3cba8a58d42ecbb438ba267315d9998'
SOURCE_NAME = 'OpenJDK21U-jdk-sources_21.0.12.1_1.tar.gz'
BINARY_META = 'OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.12.1_1.tar.gz.json'
SOURCE_SHA = '573057d03584ae793fb7ec9a14c76d826d9187a53efeefd99da47403a5308234'
ASSETS = {
    SOURCE_NAME: (RELEASE + SOURCE_NAME, SOURCE_SHA),
    SOURCE_NAME + '.json': (RELEASE + SOURCE_NAME + '.json', '24d4ee17af9c5dc7efad5020c36b2ad22050ee9da0dedfe826a7d087b01a05d1'),
    BINARY_META: (RELEASE + BINARY_META, '4924521fdaa3b68b623f3cfeaac473e8ae3841617d0dfd659aa3b4489b4f1dfe'),
    'temurin-build-e6ba7dec.tar.gz': ('https://codeload.github.com/adoptium/temurin-build/tar.gz/' + BUILD_COMMIT,
                                    'b1340378b9ed62b32acfacd012dd069ec2b9d940fc39d3c73f32142d64e89713'),
}


def digest(path):
    result = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            result.update(block)
    return result.hexdigest()


def fetch(name, url, checksum):
    path = CACHE / name
    if not path.exists():
        partial = path.with_name(path.name + '.partial')
        subprocess.run(['curl', '--fail', '--location', '--retry', '2', '--silent', '--show-error',
                        url, '-o', str(partial)], check=True)
        if digest(partial) != checksum:
            raise ValueError('Download checksum mismatch: ' + name)
        partial.rename(path)
    if digest(path) != checksum:
        raise ValueError('Cached checksum mismatch: ' + name)
    return path


def verify_metadata(binary, source):
    expected_build = 'https://github.com/adoptium/temurin-build/commit/' + BUILD_COMMIT
    for record in (binary, source):
        if (record['scmRef'] != 'jdk-21.0.12.1+1_adopt'
                or record['buildRef'] != expected_build
                or record['version']['version'] != '21.0.12.1+1-LTS'):
            raise ValueError('Source/binary version or build commit mismatch')
    if binary['sha256'] != BINARY_SHA or source['sha256'] != SOURCE_SHA:
        raise ValueError('Source/binary asset digest mismatch')
    if binary['os'] != 'mac' or binary['arch'] != 'aarch64':
        raise ValueError('Wrong binary platform metadata')


def verify_source(path):
    with tarfile.open(path) as archive:
        names = archive.getnames()
        for suffix in ('/src/hotspot/share/runtime/thread.cpp',
                       '/src/java.base/share/classes/java/lang/Object.java',
                       '/make/conf/version-numbers.conf', '/configure', '/LICENSE', '/doc/building.md'):
            if not any(n.endswith(suffix) for n in names):
                raise ValueError('Incomplete source archive: ' + suffix)


def assemble(jdk, destination):
    if destination.exists():
        raise ValueError('Output already exists; choose a new directory')
    release = (jdk / 'release').read_text()
    for marker in ('IMPLEMENTOR="Eclipse Adoptium"', 'JAVA_RUNTIME_VERSION="21.0.12.1+1-LTS"',
                   'OS_ARCH="aarch64"'):
        if marker not in release:
            raise ValueError('Selected JDK does not match pinned source materials: ' + marker)
    CACHE.mkdir(parents=True, exist_ok=True)
    files = {name: fetch(name, *details) for name, details in ASSETS.items()}
    verify_metadata(json.loads(files[BINARY_META].read_text()),
                    json.loads(files[SOURCE_NAME + '.json'].read_text()))
    verify_source(files[SOURCE_NAME])
    destination.mkdir(parents=True)
    for name, source in files.items():
        shutil.copy2(source, destination / name)
    shutil.copy2(jdk / 'release', destination / 'jdk-release.txt')
    shutil.copytree(jdk / 'legal', destination / 'legal', symlinks=True)
    manifest = {'runtime': 'Temurin 21.0.12.1+1, macOS aarch64',
                'binary_sha256': BINARY_SHA, 'build_commit': BUILD_COMMIT,
                'scm_ref': 'jdk-21.0.12.1+1_adopt',
                'assets': {name: {'url': value[0], 'sha256': value[1]} for name, value in ASSETS.items()},
                'verification': 'Pinned digests, matching upstream metadata, native/Java source and build files present',
                'limits': 'No independent rebuild of the upstream JDK; not a blanket legal certification'}
    (destination / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    (destination / 'SHA256SUMS.txt').write_text(''.join(f'{details[1]}  {name}\n' for name, details in ASSETS.items()))
    (destination / 'README.txt').write_text('''Temurin 21.0.12.1+1 corresponding source materials

This directory accompanies the unmodified Temurin source used for the bundled
macOS aarch64 Java runtime. HoDoKu's jlink module selection/options are in the
separately supplied HoDoKu source, script/build_and_run.sh.

Included: full OpenJDK sources (HotSpot, Java classes, native libraries, make and
configure files), the matching Temurin build scripts, official binary/source
metadata, selected JDK release metadata, licenses and SHA-256 checksums.
Official metadata contains the upstream build configuration and build commit.
Retain these original notices. Independent components retain their own licenses.
The Classpath Exception does not remove Java's own distribution conditions.

Unpack OpenJDK21U-jdk-sources_21.0.12.1_1.tar.gz and see its doc/building.md;
Temurin build scripts are in temurin-build-e6ba7dec.tar.gz. Building OpenJDK
requires its documented platform toolchain and boot JDK. We have verified
metadata correspondence and archive contents, not independently rebuilt Java.

For local sharing retain this whole directory with the app and HoDoKu source.
For a GitHub binary Release provide the complete directory as a separate asset
next to the matching DMG, and clearly identify it in the release notes. These
large source files need not be committed to the application's Git repository.
''')
    print('PASS: matching Temurin source, build scripts, metadata and licenses: ' + str(destination))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jdk-home', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    assemble(args.jdk_home.resolve(), args.output.resolve())
