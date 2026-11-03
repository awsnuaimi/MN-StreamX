import unittest
from benchmark_report import parse_results, render


class BenchmarkReportTest(unittest.TestCase):
    def test_median_and_fallback_runs(self):
        payload = {'benchmarks': [
            {'name': 'focus', 'metrics': {'timeNs': {'median': 2000000}}},
            {'name': 'search', 'metrics': {'timeNs': {'runs': [1000000, 3000000, 2000000]}}},
        ]}
        self.assertEqual(parse_results(payload), [('focus', 2.0), ('search', 2.0)])

    def test_rejects_missing_benchmarks(self):
        with self.assertRaises(ValueError):
            parse_results({})

    def test_skips_invalid_values(self):
        self.assertEqual(parse_results({'benchmarks': [{'name': 'bad', 'metrics': {'timeNs': {'median': -1}}}]}), [])

    def test_markdown_escaping(self):
        self.assertIn('a\\|b', render([('a|b', 1.25)], 'data.json'))


if __name__ == '__main__':
    unittest.main()