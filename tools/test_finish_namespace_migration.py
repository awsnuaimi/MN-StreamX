import hashlib
from pathlib import Path
import tempfile
import unittest
from finish_namespace_migration import apply_migration


class NamespaceMigrationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'app').mkdir()
        (self.root / 'app/build.gradle').write_text("namespace 'mnstudio.apps.mnstreamx'")
        self.entries = []
        for name in ('One', 'Two'):
            old = f'app/src/main/java/com/yourapp/iptv/{name}.kt'
            new = old.replace('com/yourapp/iptv', 'mnstudio/apps/mnstreamx')
            for path, body in ((old, 'legacy'), (new, 'replacement')):
                target = self.root / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(body)
            self.entries.append(dict(old=old, new=new,
                old_sha256=hashlib.sha256(b'legacy').hexdigest(),
                new_sha256=hashlib.sha256(b'replacement').hexdigest()))

    def test_verified_deletion_is_idempotent_and_keeps_replacements(self):
        self.assertEqual(2, apply_migration(self.root, self.entries, True))
        self.assertEqual(0, apply_migration(self.root, self.entries, True))
        self.assertTrue(all((self.root / e['new']).is_file() for e in self.entries))

    def test_preview_never_deletes_sources(self):
        self.assertEqual(2, apply_migration(self.root, self.entries))
        self.assertTrue(all((self.root / e['old']).is_file() for e in self.entries))

    def test_local_edits_abort_before_any_source_is_removed(self):
        (self.root / self.entries[1]['old']).write_text('user edit')
        with self.assertRaises(ValueError):
            apply_migration(self.root, self.entries, True)
        self.assertTrue(all((self.root / e['old']).is_file() for e in self.entries))

    def test_missing_replacement_aborts_before_deletion(self):
        (self.root / self.entries[1]['new']).unlink()
        with self.assertRaises(ValueError):
            apply_migration(self.root, self.entries, True)
        self.assertTrue((self.root / self.entries[0]['old']).exists())

    def test_path_traversal_is_rejected(self):
        entry = dict(self.entries[0], old='app/src/main/java/com/yourapp/iptv/../../../../../../outside.kt')
        entry['new'] = entry['old'].replace('com/yourapp/iptv', 'mnstudio/apps/mnstreamx')
        with self.assertRaises(ValueError):
            apply_migration(self.root, [entry], True)


if __name__ == '__main__':
    unittest.main()