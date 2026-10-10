#!/usr/bin/env python3
"""Observe a newly launched isolated test process; never kill or attach to a user instance."""
import argparse
import json
import os
import re
from pathlib import Path
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--observer", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not command:
        parser.error("provide the isolated test command after --")
    # Existing output would make a stale observer-ready marker unsafe.
    args.output.mkdir(parents=True, exist_ok=False)
    ready = args.output / "observer-ready"
    load_start = os.getloadavg()
    environment = dict(os.environ, HODOKU_OBSERVER_READY=str(ready.resolve()))
    with (args.output / "app-output.log").open("w") as log:
        process = subprocess.Popen(command, env=environment, stdout=log, stderr=subprocess.STDOUT)
        observer = subprocess.Popen([str(args.observer.resolve()), str(process.pid),
                                     str((args.output / "external.jsonl").resolve()), str(ready.resolve())],
                                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        # The fixture waits for readiness before dispatching its measured close.
        # Native app launchers do not use this marker; record readiness explicitly.
        app_result = process.wait()
        observer_output, _ = observer.communicate(timeout=10)
    metadata = {"schema": 1, "pid": str(process.pid), "app_returncode": app_result,
                "observer_returncode": observer.returncode, "observer_output": observer_output,
                "collected_epoch_ms": time.time_ns() / 1e6,
                "load_average_start": load_start, "load_average_end": os.getloadavg(),
                "boundary": "kernel NOTE_EXIT receipt; target PID only"}
    close = re.search(r"CLOSE_START wall_epoch_ms=(\d+)", (args.output / "app-output.log").read_text(errors="replace"))
    external_rows = [json.loads(line) for line in (args.output / "external.jsonl").read_text().splitlines()]
    ended = next((row for row in external_rows if row["phase"] == "process.exit"), None)
    if close and ended:
        metadata["close_dispatch_wall_epoch_ms"] = int(close.group(1))
        metadata["dispatch_to_exit_ms"] = ended["wall_epoch_ms"] - int(close.group(1))
        metadata["clock_note"] = "wall-clock alignment, 1 ms fixture quantization; includes stdout flush/dispatch; kernel notification receipt may follow exit"
    (args.output / "collection.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n")
    if app_result or observer.returncode:
        raise SystemExit("collection failed; inspect preserved logs")
    print(args.output.resolve())


if __name__ == "__main__":
    main()
