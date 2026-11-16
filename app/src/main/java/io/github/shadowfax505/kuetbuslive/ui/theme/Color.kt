package io.github.shadowfax505.kuetbuslive.ui.theme

import androidx.compose.ui.graphics.Color

// The same palette as the iOS app: near-black and white, with colour kept for meaning
// (seats available, full, errors) and for the small icon tiles in settings lists.
object Brand {
    val Black = Color(0xFF000000)
    val Ink = Color(0xFF111111)
    val Charcoal = Color(0xFF2C2C2E)
    val Grey = Color(0xFF8E8E93)
    val Silver = Color(0xFFC7C7CC)
    val Mist = Color(0xFFF2F2F7)
    val White = Color(0xFFFFFFFF)

    // Bus allocation: students in the primary colour, teachers in grey.
    val StudentBus = Color(0xFF111111)
    val TeacherBus = Color(0xFF8E8E93)

    // Meaningful colours (sage and terracotta, as on iOS).
    val Seats = Color(0xFF30A46C)
    val Full = Color(0xFFE5484D)
    val Danger = Color(0xFFE5484D)
    val Unknown = Color(0xFF8E8E93)
}

// Tile colours for list rows, like the coloured squares in iPhone Settings.
object Tile {
    val Blue = Color(0xFF0A84FF)
    val Green = Color(0xFF30A46C)
    val Orange = Color(0xFFFF9500)
    val Red = Color(0xFFFF3B30)
    val Indigo = Color(0xFF5856D6)
    val Teal = Color(0xFF30B0C7)
    val Pink = Color(0xFFFF2D55)
    val Grey = Color(0xFF8E8E93)
}
