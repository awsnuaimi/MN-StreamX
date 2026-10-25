package com.yourapp.iptv.data.update

import android.content.Context
import android.os.Build
import com.yourapp.iptv.domain.update.AppUpdate
import com.yourapp.iptv.domain.update.AppUpdateRepository
import com.yourapp.iptv.domain.update.UpdateFailure
import com.yourapp.iptv.domain.update.UpdateIssue
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

internal class GitHubAppUpdateRepository(context: Context, repository: String) : AppUpdateRepository {
    private val app = context.applicationContext
    private val http = UpdateHttp()
    private val feed = GitHubUpdateFeed(repository, http)
    private val downloads = UpdateDownload(File(app.cacheDir, "app-updates"), http)
    private val verifier = ApkUpdateVerifier(app)
    private val mutex = Mutex()

    override suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        feed.latest(verifier.installedVersion(), Build.SUPPORTED_ABIS.toList())
    }

    override suspend fun download(update: AppUpdate, progress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) { mutex.withLock {
            downloads.fetch(update, progress)
        } }

    override suspend fun verify(update: AppUpdate, file: File) = withContext(Dispatchers.IO) {
        val directory = File(app.cacheDir, "app-updates").canonicalFile
        if (file.canonicalFile.parentFile != directory || file.extension != "apk") {
            throw UpdateFailure(UpdateIssue.INTEGRITY)
        }
        val activity = currentCoroutineContext()
        try { verifier.verify(update, file) { activity.ensureActive() } }
        catch (error: UpdateFailure) { file.delete(); throw error }
    }
}