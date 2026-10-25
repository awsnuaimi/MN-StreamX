"""Publish only a manually selected, fully uploaded set of signed update APKs."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

ABIS = ("arm64-v8a", "armeabi-v7a", "x86_64")
PATTERN = re.compile(r"MN-StreamX-(1\.1\.([1-9][0-9]{0,7})\.([1-9][0-9]?))-(arm64-v8a|armeabi-v7a|x86_64)\.apk")


def gh(*arguments, payload=None):
    args = ["gh", *arguments]
    if payload is not None:
        args += ["--input", "-"]
    result = subprocess.run(args, input=json.dumps(payload) if payload is not None else None,
                            text=True, capture_output=True, check=False)
    if result.returncode:
        # Do not print credentials or a raw authenticated response in CI errors.
        raise RuntimeError("GitHub release operation failed; inspect repository permissions and release state.")
    return json.loads(result.stdout) if arguments[0] == "api" and result.stdout.strip() else None


def artifacts(directory):
    files = sorted(Path(directory).glob("*.apk"))
    if len(files) != len(ABIS):
        raise ValueError("Exactly three verified ABI APKs are required.")
    versions, abis, result = set(), set(), []
    for path in files:
        match = PATTERN.fullmatch(path.name)
        if not match or not path.is_file() or not 0 < path.stat().st_size <= 512 * 1024 * 1024:
            raise ValueError("Unexpected or empty update APK.")
        versions.add(match[1])
        abis.add(match[4])
        digest = hashlib.sha256()
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
        result.append((path, path.stat().st_size, "sha256:" + digest.hexdigest()))
    if len(versions) != 1 or abis != set(ABIS):
        raise ValueError("All three APKs must belong to the same version and different ABIs.")
    return versions.pop(), result


def version_code(version):
    match = re.fullmatch(r"1\.1\.([1-9][0-9]{0,7})\.([1-9][0-9]?)", version)
    if not match:
        raise ValueError("Unexpected version format.")
    code = 100000 + int(match[1]) * 100 + int(match[2])
    if code > 2100000000:
        raise ValueError("Version code exceeds Android's limit.")
    return code


def verify_assets(release, expected):
    if not release.get("draft") or release.get("prerelease"):
        raise ValueError("Only a draft stable release can receive update assets.")
    assets = release.get("assets", [])
    for path, size, digest in expected:
        matches = [asset for asset in assets if asset.get("name") == path.name]
        if len(matches) != 1 or matches[0].get("size") != size or matches[0].get("digest") != digest or matches[0].get("state") != "uploaded":
            raise ValueError("Uploaded APK size or SHA-256 digest differs from the verified build.")


def publish(repository, commit, directory="dist"):
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9_.-]{1,100}", repository):
        raise ValueError("Invalid repository.")
    version, expected = artifacts(directory)
    code = version_code(version)
    details = gh("api", f"repos/{repository}")
    if details.get("private") or details.get("visibility") != "public":
        raise ValueError("In-app downloads require public Releases. Repository visibility was not changed.")
    # Listing avoids treating an authentication/network failure as 'no previous release'.
    pages = gh("api", "--paginate", "--slurp", f"repos/{repository}/releases?per_page=100")
    releases = [release for page in pages for release in page]
    tag = "v" + version
    existing = None
    for release in releases:
        if release.get("tag_name") == tag:
            if not release.get("draft"):
                raise ValueError("This version is already published; published APKs are never overwritten.")
            existing = release
        if release.get("draft") or release.get("prerelease"):
            continue
        try:
            previous = version_code(release.get("tag_name", "").removeprefix("v"))
        except ValueError:
            continue
        if previous >= code:
            raise ValueError("A newer or equal stable update is already published.")
    if existing is None:
        existing = gh("api", "--method", "POST", f"repos/{repository}/releases", payload={
            "tag_name": tag, "target_commitish": commit, "name": "MN StreamX " + version,
            "body": "Signed update APKs for Android TV and Android. The app selects the compatible ABI.",
            "draft": True, "prerelease": False,
        })
    gh("release", "upload", tag, *[str(item[0]) for item in expected], "--repo", repository, "--clobber")
    endpoint = f"repos/{repository}/releases/{existing['id']}"
    ready = gh("api", endpoint)
    verify_assets(ready, expected)
    # Publishing happens after all three remote SHA-256 digests have been checked.
    gh("api", "--method", "PATCH", endpoint,
       payload={"draft": False, "prerelease": False, "make_latest": "true"})
    print(f"Published MN StreamX {version}; all three APK digests verified.")


if __name__ == "__main__":
    publish(os.environ["GITHUB_REPOSITORY"], os.environ["GITHUB_SHA"])