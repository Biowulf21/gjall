package dev.octoshrimpy.quik.common.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Switches the launcher label between "Messages" and "Gjall" by enabling one of the two
 * launcher activity-aliases in the manifest. The SMS-role intent filters live on the real
 * activities, so they are never affected.
 */
object LauncherName {

    const val DEFAULT_ALIAS = "dev.octoshrimpy.quik.feature.main.LauncherDefault"
    const val BRAND_ALIAS = "dev.octoshrimpy.quik.feature.main.LauncherBrand"

    /** Alias -> component state. Enabled entry first so the launcher never has zero entries. */
    fun states(brand: Boolean): Map<String, Int> = linkedMapOf(
        (if (brand) BRAND_ALIAS else DEFAULT_ALIAS) to PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        (if (brand) DEFAULT_ALIAS else BRAND_ALIAS) to PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    )

    fun apply(context: Context, brand: Boolean) {
        states(brand).forEach { (alias, state) ->
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName, alias), state, PackageManager.DONT_KILL_APP)
        }
    }

}
