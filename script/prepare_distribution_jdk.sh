#!/usr/bin/env bash
# Project-local, checksum-pinned toolchain; does not install or change system Java.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
if [[ "$(uname -m)" != arm64 ]]; then
    echo 'The pinned sharing toolchain targets Apple Silicon (arm64).' >&2
    exit 1
fi
CACHE="$ROOT_DIR/build/toolchains/temurin-21.0.12.1"
ARCHIVE="$CACHE/jdk.tar.gz"
JDK="$CACHE/jdk-21.0.12.1+1/Contents/Home"
URL='https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.12.1_1.tar.gz'
SHA='3623232f33a9c3baadf304480b2535f9a3cba8a58d42ecbb438ba267315d9998'
mkdir -p "$CACHE"
if [[ ! -f "$ARCHIVE" ]]; then
    curl --fail --location --retry 2 "$URL" -o "$ARCHIVE.partial" >&2
    mv "$ARCHIVE.partial" "$ARCHIVE"
fi
printf '%s  %s\n' "$SHA" "$ARCHIVE" | shasum -a 256 -c - >&2
if [[ ! -x "$JDK/bin/jpackage" ]]; then
    tar -xzf "$ARCHIVE" -C "$CACHE"
fi
printf '%s\n' "$JDK"
