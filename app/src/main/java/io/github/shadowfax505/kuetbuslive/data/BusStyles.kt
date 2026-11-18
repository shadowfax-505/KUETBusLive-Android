package io.github.shadowfax505.kuetbuslive.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf

// How the bus is drawn on the trip progress bar: seen from above like on the map,
// or from the side. Kept on this phone only.
enum class BusStyle(val raw: String, val title: String) {
    TOP("top", "From above"),
    SIDE("side", "From the side");

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: TOP
    }
}

object BusStyles {
    // Read in composition, so every screen showing the bar updates as soon as it changes.
    private val state = mutableStateOf<BusStyle?>(null)

    fun current(context: Context): BusStyle =
        state.value ?: Prefs(context).busStyle.also { state.value = it }

    fun set(context: Context, style: BusStyle) {
        Prefs(context).busStyle = style
        state.value = style
    }
}
