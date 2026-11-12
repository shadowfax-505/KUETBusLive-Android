package io.github.shadowfax505.kuetbuslive.ui.theme

import androidx.compose.ui.graphics.Color

// A monochrome palette: black, white and greys, like a modern ride app. Colour is kept
// only where it carries meaning (seats available, full, errors).
object Brand {
    val Black = Color(0xFF000000)
    val Ink = Color(0xFF111111)
    val Charcoal = Color(0xFF2B2B2B)
    val Grey = Color(0xFF6B6B6B)
    val Silver = Color(0xFFB8B8B8)
    val Mist = Color(0xFFF3F3F3)
    val White = Color(0xFFFFFFFF)

    // Bus allocation: students in black, teachers in mid grey.
    val StudentBus = Color(0xFF111111)
    val TeacherBus = Color(0xFF6B6B6B)

    // Meaningful colours.
    val Seats = Color(0xFF1E9E5A)
    val Full = Color(0xFFE5484D)
    val Danger = Color(0xFFE5484D)
    val Unknown = Color(0xFF8A8A8A)
}
