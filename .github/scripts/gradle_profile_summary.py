#!/usr/bin/env python3
"""Summarize standard Gradle --profile HTML without Gradle instrumentation.

Runs only *after* Gradle; does not touch its task graph, signing, or packages.
Task durations are not summed: tasks may run concurrently or wait for workers.
"""
import argparse
import html
from html.parser import HTMLParser
from pathlib import Path
import re


class TableReader(HTMLParser):
    def __init__(self):
        super().__init__()
        self.rows = []
        self.in_row = False
        self.in_cell = False
        self.cells = []
        self.parts = []

    def handle_starttag(self, tag, attrs):
        if tag == 'tr':
            self.in_row = True
            self.cells = []
        elif tag in ('td', 'th') and self.in_row:
            self.in_cell = True
            self.parts = []

    def handle_data(self, data):
        if self.in_cell:
            self.parts.append(data)

    def handle_endtag(self, tag):
        if tag in ('td', 'th') and self.in_cell:
            self.cells.append(html.unescape(''.join(self.parts)).strip())
            self.in_cell = False
        elif tag == 'tr' and self.in_row:
            if self.cells:
                self.rows.append(self.cells)
            self.in_row = False


def seconds(raw):
    """Parse Gradle times such as '16m3.35s', '1h2m3s', or '0s'."""
    units = {'h': 3600, 'm': 60, 's': 1}
    matches = list(re.finditer(r'(\d+(?:\.\d+)?)([hms])', raw))
    if not matches or ''.join(m.group() for m in matches) != raw:
        raise ValueError(f'Invalid duration {raw!r}')
    return sum(float(m.group(1)) * units[m.group(2)] for m in matches)


def profile(path):
    source = path.read_text(encoding='utf-8', errors='replace')
    match = re.search(r'Profiled build:\s*</td>\s*<td[^>]*>(.*?)</td>', source, flags=re.I | re.S)
    if match is None:
        match = re.search(r'Profiled build:\s*(.*?)\s*</', source, flags=re.I | re.S)
    # The profile h1 text is a more stable fallback than a hardcoded table layout.
    if match is None:
        h1 = re.search(r'<h1[^>]*>(.*?)</h1>', source, flags=re.I | re.S)
        command = re.sub(r'<[^>]*>', '', h1.group(1)).strip() if h1 else ''
    else:
        command = re.sub(r'<[^>]*>', '', match.group(1)).strip()
    tables = TableReader()
    tables.feed(source)
    durations = {}
    for cells in tables.rows:
        if len(cells) >= 2 and cells[0].startswith(':app:'):
            try:
                durations[cells[0]] = seconds(cells[1])
            except ValueError:
                continue
    top = sorted(durations.items(), key=lambda kv: kv[1], reverse=True)
    return command, top


def metrics_rows(path):
    if not path.is_file():
        return []
    lines = path.read_text(encoding='utf-8').splitlines()
    if not lines or lines[0] != 'phase\twall_seconds\texit_code':
        return []
    records = []
    for line in lines[1:]:
        parts = line.split('\t')
        if len(parts) != 3:
            continue
        phase, wall, exit_code = parts
        if phase not in ('verify', 'manifest', 'release'):
            continue
        try:
            records.append((phase, int(wall), int(exit_code)))
        except ValueError:
            continue
    return records


def render(profiles, measures):
    lines = ['MN StreamX — Gradle Release timing', '']
    for phase, wall, result in measures:
        lines.append(f'{phase}: {wall}s; exit={result}')
    if not measures:
        lines.append('No wall-time metrics found.')
    lines.append('')
    for path in sorted(profiles):
        command, top = profile(path)
        lines.append(f'Profile: {path.name} | command: {command or "(not resolved)"}')
        for name, duration in top[:12]:
            lines.append(f'  {duration:9.2f}s  {name}')
        if not top:
            lines.append('  No :app: tasks found in this profile.')
        lines.append('')
    if not profiles:
        lines.append('No Gradle HTML profiles found.')
    lines += [
        'Interpretation:',
        '* Release uses a single Gradle invocation (no manifest preflight).',
        '* Release wall time includes any tasks still needed after the preflight.',
        '* A manifest task duration in the Release profile is not a standalone CPU time measurement.',
        '* Profile task durations can overlap; do NOT add them to find wall time.',
        '* A long task duration alone cannot identify CPU usage or blocked work.',
        '* This is not a before/after speedup claim: compare multiple runner builds.',
    ]
    return '\n'.join(lines) + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profiles', type=Path, required=True)
    parser.add_argument('--metrics', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    files = list(args.profiles.glob('*.html')) if args.profiles.is_dir() else []
    report = render(files, metrics_rows(args.metrics))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(report, encoding='utf-8')
    print(report)


if __name__ == '__main__':
    main()