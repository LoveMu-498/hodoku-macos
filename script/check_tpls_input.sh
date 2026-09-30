#!/bin/bash
# Focused TPLS source/native-input verification. Never packages or installs an app.
set -euo pipefail
cd "$(dirname "$0")/.."
tpls_jdk="$PWD/build/toolchains/temurin-21.0.12.1/jdk-21.0.12.1+1/Contents/Home"
tpls_check="$(mktemp -d /tmp/hodoku-tpls-check.XXXXXX)"
mkdir -p "$tpls_check/classes" "$tpls_check/tests" "$tpls_check/home" "$tpls_check/data" "$tpls_check/tmp"
mkdir -p /tmp/hodoku-chain-edit /tmp/hodoku-chain-origin
rg --files src -g '*.java' > "$tpls_check/sources.txt"
"$tpls_jdk/bin/javac" --release 8 -encoding UTF-8 -d "$tpls_check/classes" @"$tpls_check/sources.txt"
probes=(DoodleCommandPolarityProbe TplsImmediateCoverageProbe ImmediateMappedClickProbe AnnotationDispatchDeadlineProbe ProjectedDeletionAndBoxPriorityProbe ChainBlockedPreviewProbe TplsGestureRefinementProbe ContinuousEraserWheelProbe DoodleFeedbackPolishProbe TplsClickLatencyProbe BoxSelectionToggleProbe DoodleWheelProbe ChainEditingProbe ChainRelationPreviewProbe TplsInputMappingProbe)
for probe in "${probes[@]}"; do
    "$tpls_jdk/bin/javac" --release 8 -encoding UTF-8 -cp "$tpls_check/classes:$PWD/src" -sourcepath test -d "$tpls_check/tests" "test/sudoku/$probe.java"
    "$tpls_jdk/bin/java" -Dhodoku.data.dir="$tpls_check/data" -Duser.home="$tpls_check/home" -Djava.io.tmpdir="$tpls_check/tmp" \
        -cp "$tpls_check/classes:$tpls_check/tests:$PWD/src" "sudoku.$probe" > "$tpls_check/$probe.log" 2>&1 || {
        cat "$tpls_check/$probe.log"; exit 1;
    }
    echo "PASS: $probe"
done
"$tpls_jdk/bin/javac" --release 8 -encoding UTF-8 -cp "$tpls_check/classes:$PWD/src" -sourcepath test -d "$tpls_check/tests" test/sudoku/TplsABEraseProbe.java
for variant in true; do
    "$tpls_jdk/bin/java" -Dhodoku.data.dir="$tpls_check/data" -Duser.home="$tpls_check/home" \
        -cp "$tpls_check/classes:$tpls_check/tests:$PWD/src" sudoku.TplsABEraseProbe > "$tpls_check/erase-$variant.log" 2>&1 || {
        cat "$tpls_check/erase-$variant.log"; exit 1;
    }
    echo "PASS: TplsABEraseProbe coloringSweep=$variant"
done
echo "Evidence: $tpls_check"
