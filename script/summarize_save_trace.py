#!/usr/bin/env python3
"""Summarize one operation at a time. Nested/overlapping phases must never be added together."""
import argparse
import json
from pathlib import Path


def read_rows(paths):
    rows, errors = [], []
    for path in paths:
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            try:
                rows.append(json.loads(line))
            except json.JSONDecodeError:
                errors.append(f"{path.name}:{number}: incomplete/invalid row")
    return rows, errors


def summarize(rows, external):
    grouped = {}
    for row in rows:
        grouped.setdefault((row["run_id"], row["trace_id"]), []).append(row)
    results = []
    for (run, trace), events in grouped.items():
        events.sort(key=lambda row: row["elapsed_ms"])
        first = next((r for r in events if r["event"] == "operation_start"), None)
        last = next((r for r in reversed(events) if r["event"] == "operation_end"), None)
        starts = {r["span_id"]: r for r in events if r["event"] == "phase_start"}
        ends = {r["span_id"]: r for r in events if r["event"] == "phase_end"}
        phases = sorted(ends.values(), key=lambda r: r["start_ms"])
        item = {"run_id": run, "trace_id": trace, "pid": events[0]["pid"],
                "operation": events[0]["operation"], "attempts": max(r["attempt"] for r in events),
                "outcome": last["outcome"] if last else "missing_end",
                "internal_end_ms": last["elapsed_ms"] if last else None,
                "dropped_events": max(r["dropped_events"] for r in events),
                "missing_start": first is None, "unclosed_phases": [r["phase"] for sid, r in starts.items() if sid not in ends],
                "phases": [{key: row.get(key) for key in ("phase", "attempt", "start_ms", "duration_ms", "cpu_ms", "outcome", "error_types", "start_thread", "thread", "target")} for row in phases],
                "events": [{key: r.get(key) for key in ("phase", "elapsed_ms", "attempt", "detail")} for r in events if r["event"] == "event"],
                "clock_note": "internal elapsed/durations use monotonic time; cross-process alignment uses wall time (1 ms application quantization); no summed nested phases"}
        anchors = [r["wall_epoch_ms"] - r["elapsed_ms"] for r in events]
        item["app_clock_anchor_spread_ms"] = max(anchors) - min(anchors)
        matching = [r for r in external if r.get("pid") == item["pid"]]
        exit_row = next((r for r in matching if r["phase"] == "process.exit"), None)
        # Only the final quit request for this PID can own the actual process exit.
        later_quit = any(r["pid"] == item["pid"] and r["operation"] in ("quit", "native_quit") and first is not None and r["wall_epoch_ms"] > first["wall_epoch_ms"] for r in rows if r["event"] == "operation_start")
        if first and exit_row and item["operation"] in ("quit", "native_quit") and not later_quit:
            item["external_exit_ms"] = exit_row["wall_epoch_ms"] - first["wall_epoch_ms"] + first["elapsed_ms"]
            item["after_internal_end_ms"] = item["external_exit_ms"] - last["elapsed_ms"] if last else None
            visible = [r for r in matching if r["phase"] == "window.visible_count"]
            item["observed_window_counts"] = [{"elapsed_ms": r["wall_epoch_ms"] - first["wall_epoch_ms"] + first["elapsed_ms"], "count": r["count"]} for r in visible]
            item["observer_window_sampling_ms"] = exit_row["window_sampling_ms"]
            ready = next((r for r in matching if r["phase"] == "observer.ready"), None)
            if ready:
                item["external_clock_drift_ms"] = (exit_row["wall_epoch_ms"] - ready["wall_epoch_ms"]) - (exit_row["observer_ms"] - ready["observer_ms"])
        results.append(item)
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("diagnostics", type=Path)
    parser.add_argument("--external", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    rows, errors = read_rows(sorted(args.diagnostics.glob("save-trace-*-*.jsonl")))
    external, external_errors = read_rows([args.external]) if args.external else ([], [])
    if args.external:
        observed_pids = {r["pid"] for r in external}
        rows = [r for r in rows if r["pid"] in observed_pids]
    result = {"operations": summarize(rows, external), "read_errors": errors + external_errors}
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for item in result["operations"]:
        print(f'{item["operation"]} trace={item["trace_id"]} outcome={item["outcome"]} internal={item["internal_end_ms"]}ms external={item.get("external_exit_ms")}ms')
        for row in item["phases"]:
            print(f'  attempt {row["attempt"]} {row["phase"]}: {row["duration_ms"]}ms CPU={row["cpu_ms"]}ms {row["outcome"]}')
        if item["dropped_events"] or item["missing_start"] or item["unclosed_phases"]:
            print(f'  incomplete: dropped={item["dropped_events"]}, missing_start={item["missing_start"]}, unclosed={item["unclosed_phases"]}')
    for error in result["read_errors"]:
        print(error)
    if not rows:
        raise SystemExit("no diagnostic rows; collection is not verified")


if __name__ == "__main__":
    main()
