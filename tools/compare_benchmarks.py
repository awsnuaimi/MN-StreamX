#!/usr/bin/env python3
"""Compare two AndroidX Benchmark JSON captures from the same device."""
import argparse
import json
from pathlib import Path
from benchmark_report import parse_results


def compare(baseline, candidate, threshold_percent=10.0):
    if threshold_percent < 0:
        raise ValueError('threshold must be non-negative')
    old = dict(parse_results(baseline))
    new = dict(parse_results(candidate))
    if not old or not new:
        raise ValueError('Both captures must contain valid timing measurements')
    rows = []
    for name in sorted(old.keys() | new.keys()):
        a, b = old.get(name), new.get(name)
        if a is None or b is None:
            rows.append((name, a, b, None, 'MISSING'))
        elif a == 0:
            rows.append((name, a, b, None, 'ZERO BASELINE'))
        else:
            delta = (b - a) / a * 100
            status = 'REGRESSION' if delta > threshold_percent else 'OK'
            rows.append((name, a, b, delta, status))
    return rows


def render(rows, baseline_name, candidate_name):
    lines = ['# MN StreamX — Benchmark comparison', '',
             f'Baseline: `{baseline_name}`', f'Candidate: `{candidate_name}`', '',
             '| Test | Baseline (ms) | Candidate (ms) | Change | Result |',
             '|---|---:|---:|---:|---|']
    for name, a, b, delta, status in rows:
        safe = name.replace('|', '\\|').replace('\n', ' ')
        fmt = lambda x: '—' if x is None else f'{x:.4f}'
        lines.append(f'| {safe} | {fmt(a)} | {fmt(b)} | '
                     f'{"—" if delta is None else f"{delta:+.1f}%"} | {status} |')
    lines += ['', 'Compare only runs from the same device, benchmark build and similar thermal conditions.',
              'A timing regression flag is a screening signal, not proof of a user-visible slowdown.', '']
    return '\n'.join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('baseline', type=Path)
    parser.add_argument('candidate', type=Path)
    parser.add_argument('--threshold-percent', type=float, default=10.0)
    parser.add_argument('--output', type=Path, default=Path('benchmark-comparison.md'))
    args = parser.parse_args()
    rows = compare(json.loads(args.baseline.read_text(encoding='utf-8')),
                   json.loads(args.candidate.read_text(encoding='utf-8')),
                   args.threshold_percent)
    args.output.write_text(render(rows, args.baseline.name, args.candidate.name), encoding='utf-8')
    print(f'Wrote {args.output} ({len(rows)} tests)')
    if any(row[-1] == 'REGRESSION' for row in rows):
        raise SystemExit(2)
    if any(row[-1] != 'OK' for row in rows):
        raise SystemExit(3)


if __name__ == '__main__':
    main()