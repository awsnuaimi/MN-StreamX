package com.yourapp.iptv.data.recovery

import android.app.ActivityManager
import android.content.Context
import android.util.AtomicFile
import java.io.File

/** Only Android's successful complete app-data erase removes this marker. It is never auto-cleared. */
internal class DeviceAppResetJournal(private val file: File) : AppResetJournal {
    private val atomic = AtomicFile(file)
    override fun pending() = listOf(file,File(file.path + ".bak"),File(file.path + ".new")).any { it.exists() }
    override fun mark() {
        check(file.parentFile?.isDirectory == true || file.parentFile?.mkdirs() == true)
        val output = atomic.startWrite()
        try { output.write("MN-StreamX/full-reset/v1".toByteArray(Charsets.US_ASCII));atomic.finishWrite(output) }
        catch (error: Exception) { atomic.failWrite(output);throw error }
        check(pending())
    }
}

/** No package argument: the platform operation is restricted to this application's own data. */
internal class AndroidAppDataEraser(context: Context) : AppDataEraser {
    private val app = context.applicationContext
    override fun request() = app.getSystemService(ActivityManager::class.java)?.clearApplicationUserData() == true
}