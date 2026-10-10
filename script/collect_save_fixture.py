#!/usr/bin/env python3
"""Collect real closes from the packaged runtime and a validated, isolated synthetic fixture."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import statistics
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--app", type=Path, required=True)
    parser.add_argument("--test-classes", type=Path, required=True)
    parser.add_argument("--observer", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--count", type=int, default=3, choices=range(1, 11))
    args = parser.parse_args()
    base = args.output.resolve()
    base.mkdir(parents=True, exist_ok=False)
    app = args.app.resolve()
    java = app / "Contents/runtime/Contents/Home/bin/java"
    jar = app / "Contents/app/Hodoku.jar"
    cp = str(jar) + ":" + str(args.test_classes.resolve())
    scripts = Path(__file__).resolve().parent

    def command(root, mode):
        return [str(java), "--add-exports=java.desktop/com.apple.laf=ALL-UNNAMED", "-Xmx512m", "-Duser.language=zh",
                "-Duser.home=" + str(root / "home"), "-Dhodoku.data.dir=" + str(root / "data"),
                "-Djava.io.tmpdir=" + str(root / "tmp"), "-cp", cp, "sudoku.SaveClosePerformanceProbe", mode, str(root / "probe")]

    def run(command, log):
        result = subprocess.run(command, capture_output=True, text=True)
        log.write_text(result.stdout + result.stderr)
        if result.returncode:
            raise SystemExit("failed; inspect " + str(log))

    seed = base / "seed"
    for part in ("home", "data", "tmp"):
        (seed / part).mkdir(parents=True)
    run(command(seed, "seed"), seed / "seed.log")
    samples = []
    for mode in ("close", "completed-close"):
        for at in range(1, args.count + 1):
            root = base / f"{mode}-{at}"
            for part in ("home", "tmp"):
                (root / part).mkdir(parents=True)
            shutil.copytree(seed / "data", root / "data")
            run([sys.executable, str(scripts / "collect_save_timings.py"), "--observer", str(args.observer.resolve()),
                 "--output", str(root / "collection"), "--"] + command(root, mode), root / "runner.log")
            run(command(root, "verify-completed" if mode == "completed-close" else "verify"), root / "readback.log")
            run([sys.executable, str(scripts / "summarize_save_trace.py"), str(root / "data/diagnostics"),
                 "--external", str(root / "collection/external.jsonl"), "--output", str(root / "summary.json")], root / "summary.txt")
            data = json.loads((root / "summary.json").read_text())
            trace = next(item for item in data["operations"] if item["operation"] == "quit")
            if data["read_errors"] or trace["dropped_events"] or trace["unclosed_phases"] or trace["outcome"] != "quit_requested":
                raise SystemExit("incomplete collection; inspect " + str(root))
            if abs(trace["external_clock_drift_ms"]) > 5 or trace["app_clock_anchor_spread_ms"] > 5:
                raise SystemExit("clock alignment uncertain; inspect " + str(root))
            collection = json.loads((root / "collection/collection.json").read_text())
            samples.append(dict(case=root.name, trace=trace, collection=collection))
            print(root.name, round(trace["external_exit_ms"], 3), "ms", flush=True)
    results = dict(jar_sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),
                   fixture="80 strokes x 120 exact-double points; 1201 valid frames, or 1202 after completion; R1C3=4",
                   samples=samples,
                   medians_ms={mode: statistics.median(s["trace"]["external_exit_ms"] for s in samples if s["case"].startswith(mode + "-")) for mode in ("close", "completed-close")},
                   scope="same-run application phases + external kernel notification; synthetic data; no optimization comparison")
    (base / "measurements.json").write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    main()
