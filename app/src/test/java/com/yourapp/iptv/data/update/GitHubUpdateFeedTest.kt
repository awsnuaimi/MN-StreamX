package com.yourapp.iptv.data.update

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.yourapp.iptv.domain.update.UpdateFailure
import com.yourapp.iptv.domain.update.UpdateIssue
import org.junit.Assert.*
import org.junit.Test

class GitHubUpdateFeedTest {
    private val repository = "synthetic-owner/project"
    private fun release(abis: List<String> = listOf("arm64-v8a", "armeabi-v7a", "x86_64")): JsonObject =
        JsonObject().apply {
            addProperty("tag_name", "v1.1.42.1"); addProperty("draft", false); addProperty("prerelease", false)
            add("assets", JsonArray().apply { abis.forEach { abi -> add(JsonObject().apply {
                val name = "MN-StreamX-1.1.42.1-$abi.apk"
                addProperty("name", name); addProperty("state", "uploaded"); addProperty("size", 1024)
                addProperty("digest", "sha256:" + "a".repeat(64))
                addProperty("browser_download_url", "https://github.com/$repository/releases/download/v1.1.42.1/$name")
            }) } })
        }
    private fun parse(json: JsonObject, installed: Long = 2, abis: List<String> = listOf("arm64-v8a")) =
        GitHubUpdateFeed.parse(json.toString(), repository, installed, abis)

    @Test fun selectsThePreferredSupportedAbiAndExactMonotonicVersion() {
        val update = checkNotNull(parse(release(), abis = listOf("arm64-v8a", "armeabi-v7a")))
        assertEquals("arm64-v8a", update.abi)
        assertEquals(104201L, update.versionCode)
        assertEquals("1.1.42.1", update.versionName)
    }
    @Test fun canUse32BitApkWhenTheDeviceAlsoSupportsIt() {
        assertEquals("armeabi-v7a", parse(release(listOf("armeabi-v7a")),
            abis = listOf("arm64-v8a", "armeabi-v7a"))?.abi)
    }
    @Test fun equalOrOlderReleasesCannotTriggerADowngrade() {
        assertNull(parse(release(), installed = 104201))
        assertNull(parse(release(), installed = 104301))
    }
    @Test fun noCompatibleAbiIsAReadableFailure() {
        fails(UpdateIssue.NO_COMPATIBLE_APK) { parse(release(listOf("x86_64"))) }
    }
    @Test fun rejectsDraftsPrereleasesMissingDigestsAndForeignDownloadUrls() {
        for (field in listOf("draft", "prerelease")) {
            fails(UpdateIssue.INVALID_RELEASE) { parse(release().apply { addProperty(field, true) }) }
        }
        fails(UpdateIssue.INVALID_RELEASE) { parse(release().apply {
            getAsJsonArray("assets")[0].asJsonObject.remove("digest")
        }) }
        fails(UpdateIssue.INVALID_RELEASE) { parse(release().apply {
            getAsJsonArray("assets")[0].asJsonObject.addProperty("browser_download_url", "https://example.invalid/update.apk")
        }) }
    }
    @Test fun rejectsOversizedAssetsDuplicateNamesAndMalformedTags() {
        fails(UpdateIssue.INVALID_RELEASE) { parse(release().apply {
            getAsJsonArray("assets")[0].asJsonObject.addProperty("size", Long.MAX_VALUE)
        }) }
        fails(UpdateIssue.INVALID_RELEASE) { parse(release().apply {
            val assets = getAsJsonArray("assets"); assets.add(assets[0].deepCopy())
        }) }
        fails(UpdateIssue.INVALID_RELEASE) { parse(release().apply { addProperty("tag_name", "v1.1.42.0") }) }
        fails(UpdateIssue.INVALID_RELEASE) { parse(release().apply { addProperty("tag_name", "v1.1.99999999.1") }) }
    }
    @Test fun repositoryNamesCannotInjectAUrlOrPathTraversal() {
        for (value in listOf("https://example.invalid/repo", "owner/..", "../repo", "owner/repo/extra", "owner/repo?token=x")) {
            assertFalse(value, GitHubUpdateFeed.validRepository(value))
        }
        assertTrue(GitHubUpdateFeed.validRepository(repository))
    }
    private fun fails(issue: UpdateIssue, action: () -> Unit) {
        try { action(); fail("Expected $issue") }
        catch (error: UpdateFailure) { assertEquals(issue, error.issue) }
    }
}