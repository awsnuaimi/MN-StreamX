package mnstudio.apps.mnstreamx.player.caption

import android.app.ActivityManager
import android.content.*
import android.os.IBinder
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PolishCaptionProcessTest {
    @Test fun playbackProcessSurvivesCaptionProcessDeath() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connected = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) { connected.countDown() }
            override fun onServiceDisconnected(name: ComponentName) { disconnected.countDown() }
        }
        val host = Process.myPid()
        val bound = context.bindService(Intent(context, PolishCaptionService::class.java), connection,
            Context.BIND_AUTO_CREATE or Context.BIND_WAIVE_PRIORITY)
        assertTrue(bound)
        try {
            assertTrue(connected.await(12, TimeUnit.SECONDS))
            val manager = requireNotNull(context.getSystemService(ActivityManager::class.java))
            val worker = manager.runningAppProcesses.single {
                it.uid == Process.myUid() && it.processName == context.packageName + ":polish_captions"
            }
            assertNotEquals(host, worker.pid)
            Process.killProcess(worker.pid)
            assertTrue(disconnected.await(12, TimeUnit.SECONDS))
            assertEquals(host, Process.myPid())
        } finally { context.unbindService(connection) }
    }
}