package com.arindam.mileage

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = MileageApp::class)
class OfflineStartupTest {
    @Test fun startsWithoutInternetPermissionOrAutomaticBackup() {
        val app = ApplicationProvider.getApplicationContext<MileageApp>()
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
        assertFalse(info.requestedPermissions.orEmpty().contains("android.permission.INTERNET"))
        assertEquals(0, info.applicationInfo!!.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertFalse(activity.get().isFinishing)
        activity.pause().stop().destroy()
    }
}
