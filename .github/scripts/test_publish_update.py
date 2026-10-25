import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import publish_update as publisher


class PublishUpdateTest(unittest.TestCase):
    def fixture(self, directory, version="1.1.42.1"):
        for abi in publisher.ABIS:
            Path(directory, f"MN-StreamX-{version}-{abi}.apk").write_bytes(b"synthetic-package-" + abi.encode())
        return publisher.artifacts(directory)

    def test_all_abis_must_have_the_same_version(self):
        with tempfile.TemporaryDirectory() as directory:
            self.fixture(directory)
            path = Path(directory, "MN-StreamX-1.1.42.1-x86_64.apk")
            path.rename(path.with_name("MN-StreamX-1.1.43.1-x86_64.apk"))
            with self.assertRaises(ValueError):
                publisher.artifacts(directory)

    def test_remote_digest_and_size_are_required_before_publishing(self):
        with tempfile.TemporaryDirectory() as directory:
            _, expected = self.fixture(directory)
            assets = [{"name": path.name, "size": size, "digest": digest, "state": "uploaded"} for path, size, digest in expected]
            release = {"draft": True, "prerelease": False, "assets": assets}
            publisher.verify_assets(release, expected)
            assets[0]["digest"] = "sha256:" + "0" * 64
            with self.assertRaises(ValueError):
                publisher.verify_assets(release, expected)

    def test_private_repository_is_never_published_or_made_public(self):
        with tempfile.TemporaryDirectory() as directory:
            self.fixture(directory)
            with patch.object(publisher, "gh", return_value={"private": True, "visibility": "private"}) as api:
                with self.assertRaises(ValueError):
                    publisher.publish("owner/project", "synthetic-commit", directory)
                self.assertEqual(1, api.call_count)

    def test_an_older_build_cannot_replace_a_newer_stable_release(self):
        with tempfile.TemporaryDirectory() as directory:
            self.fixture(directory)
            with patch.object(publisher, "gh", side_effect=[{"private": False, "visibility": "public"},
                    [[{"tag_name": "v1.1.43.1", "draft": False, "prerelease": False}]]]) as api:
                with self.assertRaises(ValueError):
                    publisher.publish("owner/project", "synthetic-commit", directory)
                self.assertEqual(2, api.call_count)

    def test_complete_release_remains_a_draft_until_all_assets_are_verified(self):
        with tempfile.TemporaryDirectory() as directory:
            _, expected = self.fixture(directory)
            remote = {"id": 7, "draft": True, "prerelease": False,
                      "assets": [{"name": p.name, "size": s, "digest": d, "state": "uploaded"} for p, s, d in expected]}
            with patch.object(publisher, "gh", side_effect=[{"private": False, "visibility": "public"},
                    [[]], {"id": 7}, None, remote, {}]) as api:
                publisher.publish("owner/project", "synthetic-commit", directory)
                self.assertTrue(api.call_args_list[2].kwargs["payload"]["draft"])
                self.assertFalse(api.call_args_list[-1].kwargs["payload"]["draft"])

    def test_private_source_can_publish_to_a_separate_public_package_repository(self):
        with tempfile.TemporaryDirectory() as directory:
            _, expected = self.fixture(directory)
            remote = {"id": 7, "draft": True, "prerelease": False,
                      "assets": [{"name": p.name, "size": size, "digest": digest, "state": "uploaded"}
                                 for p, size, digest in expected]}
            with patch.object(publisher, "gh", side_effect=[{"private": False, "visibility": "public", "default_branch": "main"},
                    [[]], {"id": 7}, None, remote, {}]) as api:
                publisher.publish("owner/packages", "private-source-sha", directory, source_repository="owner/private-source")
                created = api.call_args_list[2]
                self.assertEqual("repos/owner/packages/releases", created.args[-1])
                self.assertEqual("main", created.kwargs["payload"]["target_commitish"])
                self.assertNotIn("private-source-sha", str(api.call_args_list))

    def test_environment_target_is_used_consistently_with_the_apk_build(self):
        with patch.dict("os.environ", {"GITHUB_REPOSITORY": "owner/private-source", "GITHUB_SHA": "source-sha",
                                      "MN_STREAMX_UPDATE_REPOSITORY": "owner/packages"}, clear=True):
            with patch.object(publisher, "publish") as publish:
                publisher.publish_from_environment()
                publish.assert_called_once_with("owner/packages", "source-sha", source_repository="owner/private-source")

    def test_an_uninitialized_distribution_repository_cannot_receive_a_release(self):
        with tempfile.TemporaryDirectory() as directory:
            self.fixture(directory)
            with patch.object(publisher, "gh", return_value={"private": False, "visibility": "public"}) as api:
                with self.assertRaises(ValueError):
                    publisher.publish("owner/packages", "private-sha", directory, source_repository="owner/source")
                self.assertEqual(1, api.call_count)

    def test_default_publishing_still_targets_the_source_repository(self):
        with patch.dict("os.environ", {"GITHUB_REPOSITORY": "owner/source", "GITHUB_SHA": "sha"}, clear=True):
            with patch.object(publisher, "publish") as publish:
                publisher.publish_from_environment()
                publish.assert_called_once_with("owner/source", "sha", source_repository="owner/source")


if __name__ == "__main__":
    unittest.main()