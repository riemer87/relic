package de.riemer87.relic

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Das Versprechen „Relic hat keinen Internetzugriff“ (CLAUDE.md, Rahmen) – geprüft an der fertig
 * installierten App, also inklusive allem, was Bibliotheken mitbringen.
 */
@RunWith(AndroidJUnit4::class)
class PrivacyTest {

    private fun requestedPermissions(): List<String> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        return info.requestedPermissions.orEmpty().toList()
    }

    @Test
    fun relicHasNoInternetAccess() {
        assertFalse(requestedPermissions().toString(), "android.permission.INTERNET" in requestedPermissions())
    }

    @Test
    fun relicDoesNotEvenLookAtTheNetwork() {
        assertFalse(requestedPermissions().toString(), "android.permission.ACCESS_NETWORK_STATE" in requestedPermissions())
    }
}
