#!/usr/bin/env python3
"""Summarize AndroidX Benchmark JSON without external dependencies."""
import argparse
import json
import math
from pathlib import Path
import statistics


def parse_results(payload):
    if not isinstance(payload, dict) or not isinstance(payload.get('benchmarks'), list):
        raise ValueError('Expected AndroidX benchmark JSON with a benchmarks array')
    rows = []
    for entry in payload['benchmarks']:
        if not isinstance(entry, dict):
            continue
        name = entry.get('name') or entry.get('className', 'unknown')
        metrics = entry.get('metrics', {})
        if not isinstance(metrics, dict):
            continue
        timing = metrics.get('timeNs', {})
        if not isinstance(timing, dict):
            continue
        median = timing.get('median')
        if median is None:
            samples = timing.get('runs', [])
            if isinstance(samples, list) and samples:
                valid = [float(x) for x in samples if isinstance(x, (int, float)) and math.isfinite(x) and x >= 0]
                median = statistics.median(valid) if valid else None
        if not isinstance(median, (int, float)) or not math.isfinite(median) or median < 0:
            continue
        rows.append((str(name), float(median) / 1_000_000))
    return sorted(rows)


def render(rows, source):
    lines = ['# MN StreamX — Device benchmark results', '', f'Source: `{source}`', '',
             '| Benchmark | Median (ms) |', '|---|---:|']
    for name, milliseconds in rows:
        safe_name = name.replace('|', '\\|').replace('\n', ' ')
        lines.append(f'| {safe_name} | {milliseconds:.4f} |')
    lines += ['', 'These are operation-level microbenchmarks, not UI frame times or startup measurements.',
              'Compare runs on the same device and build under similar thermal conditions.', '']
    return '\n'.join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('json_file', type=Path)
    parser.add_argument('--output', type=Path, default=Path('benchmark-report.md'))
    args = parser.parse_args()
    rows = parse_results(json.loads(args.json_file.read_text(encoding='utf-8')))
    if not rows:
        parser.error('No valid timeNs median/runs values found')
    args.output.write_text(render(rows, args.json_file.name), encoding='utf-8')
    print(f'Wrote {args.output} ({len(rows)} benchmarks)')


if __name__ == '__main__':
    main()