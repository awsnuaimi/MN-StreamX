#!/usr/bin/env python3
"""Summarize ccache counters without instrumenting the Android build.

The output contains no source paths or cache keys. Missing counters are
reported as unknown rather than implying successful cache reuse.
"""
import argparse
from pathlib import Path
import re


def _metric(source: str, label: str):
    # ccache 4.x emits "Hits: 12 / 40" or "Hits  12 / 40".
    # Match the whole label to avoid confusing Direct hits with total hits.
    pattern = re.compile(r"^\s*" + re.escape(label) + r"\s*:?\s+(\d[\d,]*)\b", re.I | re.M)
    match = pattern.search(source)
    return int(match.group(1).replace(',', '')) if match else None


def parse_stats(source: str):
    hits = _metric(source, 'Hits')
    if hits is None:
        # Some older ccache versions list only direct/preprocessed hits.
        direct = _metric(source, 'cache hit (direct)')
        preprocessed = _metric(source, 'cache hit (preprocessed)')
        if direct is not None or preprocessed is not None:
            hits = (direct or 0) + (preprocessed or 0)
    misses = _metric(source, 'Misses')
    if misses is None:
        misses = _metric(source, 'cache miss')
    cacheable = _metric(source, 'Cacheable calls')
    return {'hits': hits, 'misses': misses, 'cacheable': cacheable}


def restore_state(cache_hit: str, matched_key: str):
    if cache_hit.lower() == 'true':
        return 'Exact cache snapshot restored'
    if matched_key:
        return 'Compatible earlier cache snapshot restored'
    return 'No restored cache snapshot (or restore unavailable)'


def render(source: str, cache_hit: str, matched_key: str, enabled: str):
    stats = parse_stats(source)
    output = ['MN StreamX — native C/C++ cache report',
              'Compiler launcher enabled: ' + ('yes' if enabled == '1' else 'no'),
              'Restore: ' + restore_state(cache_hit, matched_key),
              'Cacheable compilations: ' + (str(stats['cacheable']) if stats['cacheable'] is not None else 'not reported'),
              'Hits: ' + (str(stats['hits']) if stats['hits'] is not None else 'not reported'),
              'Misses: ' + (str(stats['misses']) if stats['misses'] is not None else 'not reported')]
    hits = stats['hits']
    misses = stats['misses']
    if enabled == '1' and hits is not None and misses is not None and hits + misses > 0:
        output.append(f'Hit rate (hits / (hits + misses)): {hits * 100 / (hits + misses):.1f}%')
    else:
        output.append('Hit rate: not measurable from this report')
    output += ['', 'Notes:',
               '- Hit rate is not the same as Release wall-time improvement.',
               '- First build may have mostly misses; compare two similar successful builds.',
               '- CMake, linking, configuration, and test execution are not accelerated by ccache.',
               '- Cache keys and full compiler paths are deliberately omitted.']
    return '\n'.join(output) + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stats', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--restore-hit', default='')
    parser.add_argument('--matched-key', default='')
    parser.add_argument('--enabled', default='0')
    parser.add_argument('--step-summary', type=Path)
    args = parser.parse_args()
    source = args.stats.read_text(encoding='utf-8', errors='replace') if args.stats.is_file() else ''
    report = render(source, args.restore_hit, args.matched_key, args.enabled)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(report, encoding='utf-8')
    if args.step_summary:
        with args.step_summary.open('a', encoding='utf-8') as stream:
            stream.write('### Native C/C++ cache\n\n```text\n' + report + '```\n')
    print(report)


if __name__ == '__main__':
    main()