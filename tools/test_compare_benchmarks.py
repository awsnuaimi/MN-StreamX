import unittest
from compare_benchmarks import compare, render


def capture(**values):
    return {'benchmarks': [{'name': name, 'metrics': {'timeNs': {'median': ms * 1_000_000}}}
                           for name, ms in values.items()]}


class CompareBenchmarksTest(unittest.TestCase):
    def test_detects_regression(self):
        rows = compare(capture(search=10), capture(search=12))
        self.assertEqual(rows[0][-1], 'REGRESSION')
        self.assertAlmostEqual(rows[0][3], 20)

    def test_accepts_improvement(self):
        self.assertEqual(compare(capture(search=10), capture(search=8))[0][-1], 'OK')

    def test_missing_benchmark_is_not_silently_ignored(self):
        self.assertEqual(compare(capture(search=10), capture(focus=10))[0][-1], 'MISSING')

    def test_zero_baseline_is_not_divided(self):
        self.assertEqual(compare(capture(search=0), capture(search=1))[0][-1], 'ZERO BASELINE')

    def test_rejects_empty_capture(self):
        with self.assertRaises(ValueError):
            compare(capture(), capture(search=1))

    def test_rejects_negative_threshold(self):
        with self.assertRaises(ValueError):
            compare(capture(search=1), capture(search=1), -1)

    def test_escapes_markdown_names(self):
        self.assertIn('a\\|b', render(compare(capture(**{'a|b': 1}),
                                            capture(**{'a|b': 1})), 'old.json', 'new.json'))


if __name__ == '__main__':
    unittest.main()