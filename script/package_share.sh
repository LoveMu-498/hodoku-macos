#!/usr/bin/env bash
# Build a fresh, independently audited Apple Silicon sharing artifact.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MODE="${1:---local}"
APP_VERSION="$(sed -n 's/^version=//p' "$ROOT_DIR/src/version.properties")"
case "$MODE" in
    --local|--github-release) ;;
    *) echo "usage: $0 [--local|--github-release] (prepare only; never uploads)" >&2; exit 2 ;;
esac
export HODOKU_JAVA_HOME="${HODOKU_JAVA_HOME:-$(bash "$ROOT_DIR/script/prepare_distribution_jdk.sh")}"
# Ignore shell Java injection settings when building a distributable.
unset JAVA_TOOL_OPTIONS _JAVA_OPTIONS JDK_JAVA_OPTIONS CLASSPATH
RELEASE_ID="$(date +%Y%m%d-%H%M%S)-$$"
RELEASE="$ROOT_DIR/dist/share-$RELEASE_ID"
export HODOKU_BUILD_DIR="$ROOT_DIR/build/share-$RELEASE_ID"
export HODOKU_DIST_DIR="$RELEASE/image"
mkdir -p "$RELEASE/reports"
JAVA_MATERIALS="$RELEASE/java-source-materials"
python3 "$ROOT_DIR/script/prepare_java_sources.py" \
    --jdk-home "$HODOKU_JAVA_HOME" --output "$JAVA_MATERIALS"
bash "$ROOT_DIR/script/build_and_run.sh" --build-only
APP="$HODOKU_DIST_DIR/HoDoKu.app"
cp "$ROOT_DIR/docs/distribution/安装与分享说明.txt" "$HODOKU_DIST_DIR/安装与分享说明.txt"
cp "$ROOT_DIR/COPYING" "$HODOKU_DIST_DIR/LICENSE-HoDoKu.txt"
for notice in UPSTREAM.md CHANGES.md THIRD_PARTY_NOTICES.md; do
    cp "$ROOT_DIR/$notice" "$HODOKU_DIST_DIR/$notice"
done
ln -s /Applications "$HODOKU_DIST_DIR/Applications"
# Include actual modified sources, not git archive HEAD or an unrelated old ZIP.
python3 "$ROOT_DIR/script/prepare_github_source.py" --output "$RELEASE/source-export"
cp "$RELEASE/source-export/HoDoKu-source.tar.gz" "$HODOKU_DIST_DIR/HoDoKu-source.tar.gz"
if [[ "$MODE" == --local ]]; then
    mv "$JAVA_MATERIALS" "$HODOKU_DIST_DIR/Java对应源码"
    printf '本地完整包：Java 对应源码、构建脚本、许可与校验值位于同目录的 Java对应源码。\n' \
        > "$HODOKU_DIST_DIR/Java源码获取说明.txt"
else
    JAVA_ATTACHMENT='Temurin-21.0.12.1+1-corresponding-source.tar.gz'
    COPYFILE_DISABLE=1 tar -czf "$RELEASE/$JAVA_ATTACHMENT" -C "$RELEASE" java-source-materials
    cp "$HODOKU_DIST_DIR/HoDoKu-source.tar.gz" "$RELEASE/HoDoKu-source.tar.gz"
    printf 'GitHub Release 配套材料：请从本 DMG 所在的同一次 Release 下载 %s。\n分享者必须将该附件与 DMG 同时提供，不要只转发 DMG。\n' \
        "$JAVA_ATTACHMENT" > "$HODOKU_DIST_DIR/Java源码获取说明.txt"
    cat > "$RELEASE/RELEASE-DRAFT.md" <<EOF
# HoDoKu macOS 非官方修改版 — 待审阅的发行草稿

本目录由 --github-release 准备，尚未上传。发布前核实版权声明、
实际源码 commit/tag、修改日期和验证报告。不要把基线 commit 当作包含未提交修改的发行 commit。

必须在同一次 Release 同时提供以下文件，并保持可取得：

- HoDoKu-${APP_VERSION}-AppleSilicon.dmg（完整 app，内置 Java）
- HoDoKu-source.tar.gz（与发行物对应的当前修改版源码）
- Temurin-21.0.12.1+1-corresponding-source.tar.gz（Java 对应源码、构建脚本、许可、元数据）
- SHA256SUMS.txt

主应用 GPL-3.0-or-later；Java 与其他独立组件保留自己的许可。
仅静态/本机运行验证不代表所有 macOS、M1 或 Gatekeeper 均已实测。
EOF
fi
cp "$HODOKU_BUILD_DIR/bundle-audit.json" "$RELEASE/reports/bundle-audit.json"
cp "$APP/Contents/runtime/Contents/Home/release" "$RELEASE/reports/java-release.txt"
printf 'Working-tree source snapshot; base commit: %s\nBuilt: %s\n' \
    "$(git -C "$ROOT_DIR" rev-parse HEAD)" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    > "$RELEASE/reports/build-info.txt"
DMG="$RELEASE/HoDoKu-${APP_VERSION}-AppleSilicon.dmg"
hdiutil create -volname 'HoDoKu Apple Silicon' -srcfolder "$HODOKU_DIST_DIR" \
    -format UDZO -ov "$DMG"
hdiutil verify "$DMG"
# Verify what a recipient actually receives, after mounting and copying out.
MOUNT="$(mktemp -d /tmp/hodoku-share-mount.XXXXXX)"
COPY_ROOT="$(mktemp -d /tmp/hodoku-share-copy.XXXXXX)"
cleanup() { hdiutil detach "$MOUNT" >/dev/null 2>&1 || true; }
trap cleanup EXIT
hdiutil attach -readonly -nobrowse -mountpoint "$MOUNT" "$DMG" >/dev/null
ditto "$MOUNT/HoDoKu.app" "$COPY_ROOT/HoDoKu 分享验证.app"
python3 "$ROOT_DIR/script/audit_macos_bundle.py" "$COPY_ROOT/HoDoKu 分享验证.app" \
    --report "$RELEASE/reports/received-bundle-audit.json"
python3 "$ROOT_DIR/script/verify_distribution.py" "$COPY_ROOT/HoDoKu 分享验证.app" \
    --report-dir "$RELEASE/reports/runtime"
cleanup
trap - EXIT
(cd "$RELEASE" && {
    shasum -a 256 "$(basename "$DMG")"
    if [[ "$MODE" == --github-release ]]; then
        shasum -a 256 HoDoKu-source.tar.gz "$JAVA_ATTACHMENT"
    fi
} > SHA256SUMS.txt)
printf '\nPrepared disk image (technical checks passed): %s\nChecksums and reports: %s\nBefore public release, review UPSTREAM.md and docs/open-source-release.md. No files were uploaded.\n' "$DMG" "$RELEASE"
