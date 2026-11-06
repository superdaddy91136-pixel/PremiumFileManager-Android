package com.aurora.filemanager

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test verifying package identity and strict privacy guarantees
 * (confirming that `android.permission.INTERNET` is NOT requested in the merged manifest).
 */
@RunWith(AndroidJUnit4::class)
class AuroraAppInstrumentedTest {

    @Test
    fun verifyPackageAndNoInternetPermission() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(appContext.packageName.startsWith("com.aurora.filemanager"))

        val pkgInfo = appContext.packageManager.getPackageInfo(
            appContext.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requestedPermissions = pkgInfo.requestedPermissions?.toSet() ?: emptySet()
        assertFalse(
            "Privacy violation: INTERNET permission must never be present",
            Manifest.permission.INTERNET in requestedPermissions
        )
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
