package dev.octoshrimpy.quik.common.util

import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherNameTest {

    @Test
    fun exactlyOneAliasEnabledAfterSwitch() {
        for (brand in listOf(true, false)) {
            val states = LauncherName.states(brand)
            assertEquals(setOf(LauncherName.DEFAULT_ALIAS, LauncherName.BRAND_ALIAS), states.keys)
            val enabled = states.filterValues { it == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }.keys
            assertEquals(setOf(if (brand) LauncherName.BRAND_ALIAS else LauncherName.DEFAULT_ALIAS), enabled)
        }
    }

}
