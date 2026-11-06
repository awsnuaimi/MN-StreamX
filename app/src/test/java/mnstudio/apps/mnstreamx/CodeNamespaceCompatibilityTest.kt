package mnstudio.apps.mnstreamx

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class,sdk = [30])
class CodeNamespaceCompatibilityTest {
    @Test fun codeNamespaceChangesWithoutChangingTheInstalledUpdateIdentity() {
        val app = RuntimeEnvironment.getApplication()
        assertEquals("com.yourapp.iptv",app.packageName)
        assertEquals(app.packageName,BuildConfig.APPLICATION_ID)
        assertEquals("mnstudio.apps.mnstreamx.MainActivity",MainActivity::class.java.name)
        assertEquals("MN StreamX",app.getString(R.string.app_name))
    }
    @Test fun theExistingLauncherComponentTargetsTheRenamedActivity() {
        val app = RuntimeEnvironment.getApplication()
        val alias = app.packageManager.getActivityInfo(ComponentName(app.packageName,"com.yourapp.iptv.MainActivity"),0)
        assertEquals(MainActivity::class.java.name,alias.targetActivity)
        assertTrue(alias.exported)
        val info = app.packageManager.getPackageInfo(app.packageName,PackageManager.GET_PROVIDERS)
        assertTrue(info.providers.orEmpty().any { it.name == "mnstudio.apps.mnstreamx.update.UpdateFileProvider" && it.authority == "${app.packageName}.updates" })
    }
}