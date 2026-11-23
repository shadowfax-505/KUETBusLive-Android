package io.github.shadowfax505.kuetbuslive.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import io.github.shadowfax505.kuetbuslive.R
import io.github.shadowfax505.kuetbuslive.widget.WidgetStore

// The colour of the home screen widget. Classic is black and white like the app;
// the others fill the widget with one colour, a little lighter at the top, as Apple's
// own widgets do. Each has a light and a dark version (values-night). Kept on this phone.
enum class WidgetColour(
    val raw: String,
    val title: String,
    val background: Int,
    val top: Int,
    val bottom: Int,
    val primary: Int,
    val secondary: Int,
    val hairline: Int,
) {
    CLASSIC(
        "classic", "Classic", R.drawable.widget_background,
        R.color.widget_background_top, R.color.widget_background_bottom,
        R.color.widget_primary, R.color.widget_secondary, R.color.widget_hairline,
    ),
    FOREST(
        "forest", "Forest green", R.drawable.widget_background_forest,
        R.color.widget_forest_top, R.color.widget_forest_bottom,
        R.color.widget_forest_primary, R.color.widget_forest_secondary, R.color.widget_forest_hairline,
    ),
    VIOLET(
        "violet", "Violet", R.drawable.widget_background_violet,
        R.color.widget_violet_top, R.color.widget_violet_bottom,
        R.color.widget_violet_primary, R.color.widget_violet_secondary, R.color.widget_violet_hairline,
    ),
    BUTTER(
        "butter", "Butter cream", R.drawable.widget_background_butter,
        R.color.widget_butter_top, R.color.widget_butter_bottom,
        R.color.widget_butter_primary, R.color.widget_butter_secondary, R.color.widget_butter_hairline,
    );

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: CLASSIC
    }
}

object WidgetColours {
    // Read in composition, so the picker in Profile updates as soon as it changes.
    private val state = mutableStateOf<WidgetColour?>(null)

    fun current(context: Context): WidgetColour =
        state.value ?: Prefs(context).widgetColour.also { state.value = it }

    fun set(context: Context, colour: WidgetColour) {
        Prefs(context).widgetColour = colour
        state.value = colour
        WidgetStore.refresh(context)
    }
}
