package com.pimorazelvanto.dayscounter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManifestPermissionsTest {
    @Test
    fun `merged manifest requests no exact alarm permission`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val requested =
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                .orEmpty()
                .toSet()

        val exactAlarmPermissions = setOf(Manifest.permission.USE_EXACT_ALARM, Manifest.permission.SCHEDULE_EXACT_ALARM)
        assertEquals(emptySet<String>(), requested intersect exactAlarmPermissions)
    }
}
