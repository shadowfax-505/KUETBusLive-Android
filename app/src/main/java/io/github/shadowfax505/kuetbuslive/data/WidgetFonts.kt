package io.github.shadowfax505.kuetbuslive.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import io.github.shadowfax505.kuetbuslive.R
import io.github.shadowfax505.kuetbuslive.widget.WidgetStore

// The font on the home screen widget. A widget can't change its font from code, so
// each font has its own small, medium and large layout. Kept on this phone only.
enum class WidgetFont(
    val raw: String,
    val title: String,
    val small: Int,
    val medium: Int,
    val large: Int,
    val titleFont: Int,
    val numberFont: Int,
    val numberSp: Float,    // the big number's size, as in widget_styles.xml
) {
    BOARD(
        "board", "Departure board",
        R.layout.widget_small_board, R.layout.widget_medium_board, R.layout.widget_large_board,
        R.font.barlow_condensed_bold, R.font.barlow_condensed_bold, 44f,
    ),
    GROTESK(
        "grotesk", "Grotesk",
        R.layout.widget_small_grotesk, R.layout.widget_medium_grotesk, R.layout.widget_large_grotesk,
        R.font.space_grotesk_bold, R.font.space_grotesk_bold, 36f,
    ),
    TIMETABLE(
        "timetable", "Timetable",
        R.layout.widget_small_timetable, R.layout.widget_medium_timetable, R.layout.widget_large_timetable,
        R.font.fraunces_semibold, R.font.jetbrains_mono_bold, 32f,
    );

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: BOARD
    }
}

object WidgetFonts {
    // Read in composition, so the picker in Profile updates as soon as it changes.
    private val state = mutableStateOf<WidgetFont?>(null)

    fun current(context: Context): WidgetFont =
        state.value ?: Prefs(context).widgetFont.also { state.value = it }

    fun set(context: Context, font: WidgetFont) {
        Prefs(context).widgetFont = font
        state.value = font
        WidgetStore.refresh(context)
    }
}
