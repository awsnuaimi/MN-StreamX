#!/usr/bin/env python3
"""Summarize timestamped Gradle task boundaries without collecting secrets."""

import argparse
import csv
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Interval:
    task: str
    begin: int
    end: int
    elapsed: int
    status: str


def read_timeline(path: Path) -> list[Interval]:
    if not path.exists():
        return []
    rows = []
    with path.open(encoding="utf-8", newline="") as source:
        reader = csv.DictReader(source, delimiter="\t")
        required = {"task", "start_epoch_ms", "end_epoch_ms", "elapsed_ms", "status"}
        if not required.issubset(reader.fieldnames or []):
            raise ValueError("Invalid Gradle task-timeline columns")
        for row in reader:
            try:
                a, b = int(row["start_epoch_ms"]), int(row["end_epoch_ms"])
                duration = int(row["elapsed_ms"])
            except (TypeError, ValueError) as exc:
                raise ValueError("Invalid numeric task-timeline record") from exc
            if a > b or duration < 0:
                raise ValueError("Invalid Gradle task interval")
            rows.append(Interval(row["task"], a, b, duration, row["status"]))
    return rows


def interval_union_duration(intervals: list[tuple[int, int]]) -> int:
    """Return total covered milliseconds, counting overlapping intervals once."""
    total, last_start, last_end = 0, None, None
    for start, end in sorted(intervals):
        if last_start is None:
            last_start, last_end = start, end
        elif start <= last_end:
            last_end = max(last_end, end)
        else:
            total += last_end - last_start
            last_start, last_end = start, end
    return total + (last_end - last_start) if last_start is not None else 0


def render_report(rows: list[Interval]) -> str:
    if not rows:
        return "No release task timeline was captured.\n"
    start = min(r.begin for r in rows)
    end = max(r.end for r in rows)
    lines = [
        "MN StreamX / Gradle Release Task Timeline",
        "Task boundary measurements only; overlap does not establish causality.",
        f"Observed task window: {(end - start) / 1000:.1f}s; task count: {len(rows)}",
        "",
        "Longest tasks (elapsed, start offset, end offset):",
    ]
    for r in sorted(rows, key=lambda x: -x.elapsed)[:20]:
        lines.append(
            f"{r.elapsed / 1000:8.1f}s  +{(r.begin - start) / 1000:7.1f}s"
            f"  +{(r.end - start) / 1000:7.1f}s  {r.status:9s}  {r.task}"
        )
    manifest = [r for r in rows if r.task.endswith(":processReleaseManifestForPackage")]
    native = [r for r in rows if ":buildCMake" in r.task]
    if manifest:
        m = manifest[0]
        overlaps = [(max(m.begin, n.begin), min(m.end, n.end)) for n in native]
        overlaps = [(a, b) for a, b in overlaps if a < b]
        overlap_ms = interval_union_duration(overlaps)
        lines.extend([
            "",
            f"Manifest task: start +{(m.begin - start) / 1000:.1f}s, "
            f"end +{(m.end - start) / 1000:.1f}s, "
            f"elapsed {m.elapsed / 1000:.1f}s",
            f"CMake tasks: {len(native)}; union overlap with Manifest: {overlap_ms / 1000:.1f}s",
            "Overlapping times alone cannot distinguish waiting, CPU/I-O contention, or Manifest processing.",
        ])
    else:
        lines.append("\nNo processReleaseManifestForPackage task recorded.")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("timeline", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = render_report(read_timeline(args.timeline))
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    print(report, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
