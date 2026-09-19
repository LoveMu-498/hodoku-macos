#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-run}"
APP_NAME="HoDoKu"
BUNDLE_ID="net.sourceforge.hodoku"
JAR_NAME="Hodoku.jar"
PUZZLE="530070000600195000098000060800060003400803001700020006060000280000419005000080079"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC_DIR="$ROOT_DIR/src"
APP_VERSION="$(sed -n 's/^version=//p' "$SRC_DIR/version.properties")"
[[ "$APP_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Invalid application version" >&2; exit 1; }
BUILD_DIR="${HODOKU_BUILD_DIR:-$ROOT_DIR/build/macos}"
CLASSES_DIR="$BUILD_DIR/classes"
INPUT_DIR="$BUILD_DIR/input"
ICONSET_DIR="$BUILD_DIR/HoDoKu.iconset"
ICNS_FILE="$BUILD_DIR/HoDoKu.icns"
VERIFY_DIR="$BUILD_DIR/verify"
TEST_CLASSES_DIR="$BUILD_DIR/test-classes"
DIST_DIR="${HODOKU_DIST_DIR:-$ROOT_DIR/dist}"
APP_BUNDLE="$DIST_DIR/$APP_NAME.app"
APP_EXECUTABLE="$APP_BUNDLE/Contents/MacOS/$APP_NAME"

# Compile, probe and package with the same explicitly selected JDK.
if [[ -n "${HODOKU_JAVA_HOME:-${JAVA_HOME:-}}" ]]; then
    export JAVA_HOME="${HODOKU_JAVA_HOME:-$JAVA_HOME}"
    for jdk_tool in java javac jar jlink jpackage; do
        if [[ ! -x "$JAVA_HOME/bin/$jdk_tool" ]]; then
            echo "Selected JDK is missing $jdk_tool: $JAVA_HOME" >&2
            exit 1
        fi
    done
    export PATH="$JAVA_HOME/bin:$PATH"
fi

require_tool() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "Required tool not found: $1" >&2
        exit 1
    fi
}

property_keys() {
    awk -F= '!/^[[:space:]]*($|#|!)/ { print $1 }' "$1"
}

property_placeholders() {
    awk -F= '!/^[[:space:]]*($|#|!)/ {
        value = substr($0, index($0, "=") + 1)
        printf "%s=", $1
        while (match(value, /\{[0-9]+\}/)) {
            printf "%s", substr(value, RSTART, RLENGTH)
            value = substr(value, RSTART + RLENGTH)
        }
        print ""
    }' "$1"
}

verify_localization() {
    local bundle_count=0
    local base_file
    local zh_file
    local duplicate_keys

    while IFS= read -r -d '' base_file; do
        bundle_count=$((bundle_count + 1))
        zh_file="${base_file%.properties}_zh.properties"
        if [[ ! -f "$zh_file" ]]; then
            echo "Missing Chinese resource bundle: $zh_file" >&2
            exit 1
        fi

        duplicate_keys="$(property_keys "$zh_file" | LC_ALL=C sort | uniq -d)"
        if [[ -n "$duplicate_keys" ]]; then
            echo "Duplicate keys in $zh_file: $duplicate_keys" >&2
            exit 1
        fi

        if ! diff -u <(property_keys "$base_file") <(property_keys "$zh_file"); then
            echo "Chinese resource keys differ from $base_file" >&2
            exit 1
        fi

        if ! diff -u <(property_placeholders "$base_file") <(property_placeholders "$zh_file"); then
            echo "Message placeholders differ in $zh_file" >&2
            exit 1
        fi

        if LC_ALL=C grep -n '[^ -~	]' "$zh_file" >/dev/null; then
            echo "Chinese properties must use Java 8 Unicode escapes: $zh_file" >&2
            exit 1
        fi
    done < <(find "$SRC_DIR/intl" -maxdepth 1 -type f -name '*.properties' \
        ! -name '*_de.properties' ! -name '*_zh.properties' -print0 | LC_ALL=C sort -z)

    if [[ "$bundle_count" -ne 41 ]]; then
        echo "Expected 41 base resource bundles, found $bundle_count" >&2
        exit 1
    fi
    if [[ ! -f "$SRC_DIR/help/keyboard_zh.html" ]]; then
        echo "Missing Chinese keyboard help" >&2
        exit 1
    fi
}

create_icon() {
    local source_icon="$SRC_DIR/img/hodoku02-256.png"
    mkdir -p "$ICONSET_DIR"

    sips -z 16 16 "$source_icon" --out "$ICONSET_DIR/icon_16x16.png" >/dev/null
    sips -z 32 32 "$source_icon" --out "$ICONSET_DIR/icon_16x16@2x.png" >/dev/null
    sips -z 32 32 "$source_icon" --out "$ICONSET_DIR/icon_32x32.png" >/dev/null
    sips -z 64 64 "$source_icon" --out "$ICONSET_DIR/icon_32x32@2x.png" >/dev/null
    sips -z 128 128 "$source_icon" --out "$ICONSET_DIR/icon_128x128.png" >/dev/null
    sips -z 256 256 "$source_icon" --out "$ICONSET_DIR/icon_128x128@2x.png" >/dev/null
    sips -z 256 256 "$source_icon" --out "$ICONSET_DIR/icon_256x256.png" >/dev/null
    sips -z 512 512 "$source_icon" --out "$ICONSET_DIR/icon_256x256@2x.png" >/dev/null
    sips -z 512 512 "$source_icon" --out "$ICONSET_DIR/icon_512x512.png" >/dev/null
    sips -z 1024 1024 "$source_icon" --out "$ICONSET_DIR/icon_512x512@2x.png" >/dev/null

    iconutil -c icns "$ICONSET_DIR" -o "$ICNS_FILE"
}

build_app() {
    require_tool javac
    require_tool jar
    require_tool jpackage
    require_tool sips
    require_tool iconutil
    require_tool codesign
    require_tool python3
    require_tool xcrun

    local selected_jdk
    selected_jdk="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*java.home = //p')"
    for jdk_tool in java javac jar jlink jpackage; do
        if [[ ! -x "$selected_jdk/bin/$jdk_tool" ]]; then
            echo "A complete JDK is required: $selected_jdk" >&2
            exit 1
        fi
    done
    export PATH="$selected_jdk/bin:$PATH"
    python3 "$ROOT_DIR/script/audit_macos_bundle.py" "$selected_jdk" --runtime-only

    verify_localization

    # Keep earlier artifacts available for comparison and rollback.
    local backup_suffix="$(date +%Y%m%d-%H%M%S)-$$"
    [[ ! -e "$BUILD_DIR" ]] || mv "$BUILD_DIR" "$BUILD_DIR.previous-$backup_suffix"
    [[ ! -e "$APP_BUNDLE" ]] || mv "$APP_BUNDLE" "$APP_BUNDLE.previous-$backup_suffix"
    mkdir -p "$CLASSES_DIR" "$INPUT_DIR" "$DIST_DIR"

    find "$SRC_DIR" -name '*.java' -print0 \
        | xargs -0 javac --release 8 -encoding UTF-8 -d "$CLASSES_DIR"

    local bundle_names=()
    local base_file
    while IFS= read -r -d '' base_file; do
        bundle_names+=("$(basename "$base_file" .properties)")
    done < <(find "$SRC_DIR/intl" -maxdepth 1 -type f -name '*.properties' \
        ! -name '*_de.properties' ! -name '*_zh.properties' -print0 | LC_ALL=C sort -z)

    mkdir -p "$TEST_CLASSES_DIR"
    find "$ROOT_DIR/test" -name '*.java' -print0 \
        | xargs -0 javac --release 8 -encoding UTF-8 -cp "$CLASSES_DIR:$SRC_DIR" -d "$TEST_CLASSES_DIR"
    java -cp "$CLASSES_DIR:$TEST_CLASSES_DIR:$SRC_DIR" sudoku.LocaleShortcutProbe "${bundle_names[@]}"
	local probe_tmp
	probe_tmp="$(mktemp -d /tmp/hodoku-build-probes.XXXXXX)"
	mkdir -p "$probe_tmp/data" "$probe_tmp/home" "$probe_tmp/tmp"
	for probe in \
		ApplicationPathsProbe \
		AppearanceRenderingProbe \
		BoxReasoningRenderingProbe \
		ChainRouteGeometryProbe \
		CompletionTransitionProbe \
		SudokuReferenceParserProbe \
		MainLaunchArgumentsProbe \
		MacOSApplicationProbe \
		NativeReasoningMatcherProbe \
		CurrentReasoningProbe \
		NativeReasoningLibraryProbe \
		GroupedChainProbe \
		AlsManualChainProbe \
		ChainTextCodecProbe \
		OptionsPersistenceProbe \
        PairedPaletteInvariantProbe \
		PuzzleHistoryProbe \
		SessionStoreProbe \
		ToolbarIconRenderingProbe \
		WindowLayoutProbe; do
		java -Dhodoku.data.dir="$probe_tmp/data" -Duser.home="$probe_tmp/home" \
			-Djava.io.tmpdir="$probe_tmp/tmp" \
			-cp "$CLASSES_DIR:$TEST_CLASSES_DIR:$SRC_DIR" "sudoku.$probe"
	done
	rm -rf "$probe_tmp"

    cp -R "$SRC_DIR/intl" "$CLASSES_DIR/intl"
    cp -R "$SRC_DIR/img" "$CLASSES_DIR/img"
    cp -R "$SRC_DIR/help" "$CLASSES_DIR/help"
    cp "$SRC_DIR/version.properties" "$CLASSES_DIR/version.properties"
    cp "$SRC_DIR/templates.dat" "$CLASSES_DIR/templates.dat"
    cp "$ROOT_DIR/COPYING" "$CLASSES_DIR/COPYING"

    jar --create \
        --file "$INPUT_DIR/$JAR_NAME" \
        --main-class sudoku.Main \
        -C "$CLASSES_DIR" .

    jar tf "$INPUT_DIR/$JAR_NAME" > "$BUILD_DIR/jar-contents.txt"
    for required_path in \
        sudoku/Main.class \
        version.properties \
        templates.dat \
        intl/MainFrame.properties \
        intl/MainFrame_zh.properties \
        intl/SolutionCategory_zh.properties \
        img/hodoku02-256.png \
        help/keyboard.html \
        help/keyboard_zh.html \
        COPYING; do
        if ! grep -Fxq "$required_path" "$BUILD_DIR/jar-contents.txt"; then
            echo "Missing required JAR resource: $required_path" >&2
            exit 1
        fi
    done

    create_icon

    jpackage \
        --type app-image \
        --jlink-options "--strip-debug --no-header-files --no-man-pages" \
        --dest "$DIST_DIR" \
        --input "$INPUT_DIR" \
        --name "$APP_NAME" \
        --main-jar "$JAR_NAME" \
        --main-class sudoku.Main \
        --app-version "$APP_VERSION" \
        --vendor "HoDoKu contributors" \
        --description "Sudoku solver, generator, trainer, and analyzer" \
        --icon "$ICNS_FILE" \
        --java-options "--add-exports=java.desktop/com.apple.laf=ALL-UNNAMED" \
        --java-options "-Duser.language=zh" \
        --java-options "-Xmx512m" \
        --mac-package-identifier "$BUNDLE_ID" \
        --mac-package-name "$APP_NAME"

    /usr/libexec/PlistBuddy \
        -c "Delete :NSMicrophoneUsageDescription" \
        "$APP_BUNDLE/Contents/Info.plist"
    /usr/libexec/PlistBuddy \
        -c "Set :NSHumanReadableCopyright Copyright (C) 2008-2020 HoDoKu contributors" \
        "$APP_BUNDLE/Contents/Info.plist"
    /usr/libexec/PlistBuddy \
        -c "Set :LSMinimumSystemVersion 11.0" \
        "$APP_BUNDLE/Contents/Info.plist"
    /usr/bin/xcrun clang -isysroot "$(/usr/bin/xcrun --sdk macosx --show-sdk-path)" -fobjc-arc -arch arm64 -mmacosx-version-min=11.0 -framework Cocoa \
        "$SRC_DIR/native/ReplayShare.m" -o "$APP_BUNDLE/Contents/MacOS/HoDoKuShare"
    codesign --force --sign - "$APP_BUNDLE/Contents/MacOS/HoDoKuShare"
    codesign --force --sign - "$APP_BUNDLE"
    codesign --verify --deep --strict "$APP_BUNDLE"
    python3 "$ROOT_DIR/script/audit_macos_bundle.py" "$APP_BUNDLE" \
        --report "$BUILD_DIR/bundle-audit.json"

    echo "Built $APP_BUNDLE"
}

open_app() {
    /usr/bin/open -n "$APP_BUNDLE"
}

verify_app() {
    mkdir -p "$VERIFY_DIR"

    local gui_tmp
	local app_pid=""
	gui_tmp="$(mktemp -d /tmp/hodoku-gui-probe.XXXXXX)"
	cleanup_verify_app() {
		if [[ -n "${app_pid:-}" ]] && kill -0 "$app_pid" >/dev/null 2>&1; then
			kill -TERM "$app_pid" 2>/dev/null || true
		fi
	}
	trap cleanup_verify_app EXIT
	mkdir -p "$gui_tmp/data" "$gui_tmp/home" "$gui_tmp/tmp"
    java --add-exports=java.desktop/com.apple.laf=ALL-UNNAMED \
		-Dhodoku.data.dir="$gui_tmp/data" -Djava.io.tmpdir="$gui_tmp/tmp" \
		-Duser.home="$gui_tmp/home" -Duser.language=zh \
        -cp "$CLASSES_DIR:$TEST_CLASSES_DIR:$SRC_DIR" sudoku.GuiLocalizationProbe
	for gui_probe in ChainEdgeDeletionProbe AnnotationDeletionProbe AnnotationDeletionMouseProbe MissingCandidateDoubleClickProbe MissingCandidateMouseProbe ColoringInteractionProbe AnnotationInteractionProbe AnnotationPerformanceProbe SetAllSinglesInteractionProbe UserChainRenderingProbe ReasoningInteractionProbe CurrentReasoningInteractionProbe ToolbarChainStateProbe TechniqueSelectorProbe TechniqueSelectorNavigationProbe HintInteractionProbe KeyboardHelpDialogProbe PaletteStartupProbe SessionLifecycleProbe TransientReferenceHighlightProbe; do
		java --add-exports=java.desktop/com.apple.laf=ALL-UNNAMED \
			-Dhodoku.data.dir="$gui_tmp/data" -Djava.io.tmpdir="$gui_tmp/tmp" \
			-Duser.home="$gui_tmp/home" -Duser.language=zh \
			-cp "$CLASSES_DIR:$TEST_CLASSES_DIR:$SRC_DIR" "sudoku.$gui_probe"
	done

	JAVA_TOOL_OPTIONS="-Dhodoku.data.dir=$gui_tmp/data -Djava.io.tmpdir=$gui_tmp/tmp -Duser.home=$gui_tmp/home -Duser.language=zh" \
        "$APP_EXECUTABLE" /? > "$VERIFY_DIR/help.stdout" 2> "$VERIFY_DIR/help.stderr"
    grep -Fq "Usage: java -Xmx512m -jar hodoku.jar" "$VERIFY_DIR/help.stdout"

	JAVA_TOOL_OPTIONS="-Dhodoku.data.dir=$gui_tmp/data -Djava.io.tmpdir=$gui_tmp/tmp -Duser.home=$gui_tmp/home -Duser.language=zh" \
        "$APP_EXECUTABLE" "$PUZZLE" /o stdout \
        > "$VERIFY_DIR/solve.stdout" 2> "$VERIFY_DIR/solve.stderr"
    grep -Fq "0 puzzles unsolved!" "$VERIFY_DIR/solve.stdout"
    grep -Fq "0 puzzles not solved logically!" "$VERIFY_DIR/solve.stdout"

    local launch_app="$gui_tmp/$APP_NAME.app"
    local launch_root
    launch_root="$(cd "$gui_tmp" && pwd -P)"
    local launch_executable="$launch_root/$APP_NAME.app/Contents/MacOS/$APP_NAME"
    ditto "$APP_BUNDLE" "$launch_app"
	printf '\njava-options=-Dhodoku.data.dir=%s\njava-options=-Djava.io.tmpdir=%s\njava-options=-Duser.home=%s\n' \
		"$gui_tmp/data" "$gui_tmp/tmp" "$gui_tmp/home" \
        >> "$launch_app/Contents/app/$APP_NAME.cfg"
    codesign --force --sign - "$launch_app" >/dev/null

    /usr/bin/open -n "$launch_app"
	app_pid=""
    local attempt
    for attempt in {1..20}; do
        app_pid="$(pgrep -f -x "$launch_executable" || true)"
        if [[ -n "$app_pid" ]]; then
            break
        fi
        sleep 0.25
    done
    if [[ -z "$app_pid" ]] || ! kill -0 "$app_pid" >/dev/null 2>&1; then
        echo "$APP_NAME did not remain running after LaunchServices opened the app bundle" >&2
        exit 1
    fi
	local laf_ready=false
	for attempt in {1..40}; do
		if grep -Fq "CONFIG: laf=Mac OS X" "$gui_tmp/tmp/hodoku.log" 2>/dev/null; then
			laf_ready=true
			break
		fi
		if ! kill -0 "$app_pid" >/dev/null 2>&1; then
			break
		fi
		sleep 0.25
	done
	if [[ "$laf_ready" != true ]]; then
		echo "$APP_NAME did not finish native macOS startup" >&2
		exit 1
	fi
	if grep -Eq "IllegalAccessException|Fatal error, unable to modify" "$gui_tmp/tmp/hodoku.log"; then
		echo "Unexpected startup error in $gui_tmp/tmp/hodoku.log" >&2
		exit 1
	fi

	/usr/bin/osascript -e "tell application \"$launch_app\" to quit" \
		> "$VERIFY_DIR/quit.stdout" 2> "$VERIFY_DIR/quit.stderr" &
	local quit_apple_pid=$!
	for attempt in {1..20}; do
		if ! kill -0 "$app_pid" >/dev/null 2>&1; then
			break
		fi
		sleep 0.25
	done
	if kill -0 "$app_pid" >/dev/null 2>&1; then
		echo "$APP_NAME did not respond to the native macOS Quit event" >&2
		kill "$quit_apple_pid" 2>/dev/null || true
		kill -KILL "$app_pid"
		exit 1
	fi
	wait "$quit_apple_pid"
	if [[ ! -s "$gui_tmp/data/hodoku.hcfg" || ! -s "$gui_tmp/data/last-session.xml" ]]; then
		echo "$APP_NAME did not save options and the last session before native Quit" >&2
		exit 1
	fi

	/usr/bin/open -n "$launch_app"
	app_pid=""
	for attempt in {1..20}; do
		app_pid="$(pgrep -f -x "$launch_executable" || true)"
		if [[ -n "$app_pid" ]]; then
			break
		fi
		sleep 0.25
	done
	if [[ -z "$app_pid" ]] || ! kill -0 "$app_pid" >/dev/null 2>&1; then
		echo "$APP_NAME did not relaunch after saving its last session" >&2
		exit 1
	fi
	/usr/bin/osascript -e "tell application \"$launch_app\" to quit" \
		> "$VERIFY_DIR/relaunch-quit.stdout" 2> "$VERIFY_DIR/relaunch-quit.stderr" &
	quit_apple_pid=$!
	for attempt in {1..20}; do
		if ! kill -0 "$app_pid" >/dev/null 2>&1; then
			break
		fi
		sleep 0.25
	done
	if kill -0 "$app_pid" >/dev/null 2>&1; then
		kill "$quit_apple_pid" 2>/dev/null || true
		kill -KILL "$app_pid"
	else
		wait "$quit_apple_pid"
	fi
	rm -rf "$gui_tmp"
	trap - EXIT

	echo "Verified CLI solving, Finder launch, native Quit saving, and session relaunch"
}

build_app

case "$MODE" in
    run)
        open_app
        ;;
    --build-only|build-only)
        ;;
    --verify|verify)
        verify_app
        ;;
    --debug|debug)
        lldb -- "$APP_EXECUTABLE"
        ;;
    --logs|logs|--telemetry|telemetry)
        open_app
        /usr/bin/log stream --info --style compact --predicate "process == \"$APP_NAME\""
        ;;
    *)
        echo "usage: $0 [run|--build-only|--verify|--debug|--logs|--telemetry]" >&2
        exit 2
        ;;
esac
