package io.github.shadowfax505.kuetbuslive.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import io.github.shadowfax505.kuetbuslive.R

// The home screen icons a rider can pick in Profile. Each one is a launcher
// activity-alias in the manifest; exactly one is enabled at a time.
enum class AppIconChoice(
    val alias: String,
    val title: String,
    @DrawableRes val background: Int,
    @DrawableRes val foreground: Int,
) {
    Black("IconBlack", "Black", R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground),
    White("IconWhite", "White", R.drawable.ic_launcher_white_background, R.drawable.ic_launcher_white_foreground),
    Colour("IconColour", "Colour", R.drawable.ic_launcher_colour_background, R.drawable.ic_launcher_colour_foreground),
    Pin("IconPin", "Pin", R.drawable.ic_launcher_pin_background, R.drawable.ic_launcher_pin_foreground),
}

object AppIcons {
    private fun component(context: Context, choice: AppIconChoice) =
        ComponentName(context.packageName, "io.github.shadowfax505.kuetbuslive.${choice.alias}")

    fun current(context: Context): AppIconChoice {
        val pm = context.packageManager
        return AppIconChoice.entries.firstOrNull { choice ->
            when (pm.getComponentEnabledSetting(component(context, choice))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                // Untouched aliases keep the value from the manifest, where only Black is on.
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> choice == AppIconChoice.Black
                else -> false
            }
        } ?: AppIconChoice.Black
    }

    fun set(context: Context, choice: AppIconChoice) {
        if (choice == current(context)) return
        val pm = context.packageManager
        // Turn the new icon on before the old one off, so the app never has no launcher entry.
        pm.setComponentEnabledSetting(
            component(context, choice),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        AppIconChoice.entries.filter { it != choice }.forEach {
            pm.setComponentEnabledSetting(
                component(context, it),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
