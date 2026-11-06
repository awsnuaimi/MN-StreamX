package mnstudio.apps.mnstreamx

import android.app.Application
import android.content.ComponentName
import android.content.Intent
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
    @Test fun installedIdentityMatchesTheCodeNamespaceAndBrand() {
        val app = RuntimeEnvironment.getApplication()
        assertEquals("mnstudio.apps.mnstreamx",app.packageName)
        assertEquals(app.packageName,BuildConfig.APPLICATION_ID)
        assertEquals("mnstudio.apps.mnstreamx.MainActivity",MainActivity::class.java.name)
        assertEquals("MN StreamX",app.getString(R.string.app_name))
    }
    @Test fun launchersAndFileProvidersBelongToTheNewIdentity() {
        val app = RuntimeEnvironment.getApplication()
        val component = ComponentName(app.packageName,MainActivity::class.java.name)
        val activity = app.packageManager.getActivityInfo(component,0)
        assertNull(activity.targetActivity)
        assertTrue(activity.exported)
        assertEquals(component,app.packageManager.getLaunchIntentForPackage(app.packageName)?.component)
        val tv = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(app.packageName)
        assertTrue(app.packageManager.queryIntentActivities(tv,0).any { it.activityInfo.name == component.className })
        val info = app.packageManager.getPackageInfo(app.packageName,PackageManager.GET_PROVIDERS)
        assertTrue(info.providers.orEmpty().any { it.name == "mnstudio.apps.mnstreamx.update.UpdateFileProvider" && it.authority == "${app.packageName}.updates" })
        assertTrue(info.providers.orEmpty().any { it.name == "mnstudio.apps.mnstreamx.diagnostics.DiagnosticFileProvider" && it.authority == "${app.packageName}.diagnostics" })
    }
}