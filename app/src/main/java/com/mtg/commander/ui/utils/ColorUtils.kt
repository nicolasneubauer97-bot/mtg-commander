package com.mtg.commander.ui.utils

object ColorUtils {
    fun toGerman(colors: String): String {
        if (colors.isBlank()) return ""
        return colors.uppercase().mapNotNull { c ->
            when (c) {
                'W' -> "Weiß"
                'U' -> "Blau"
                'B' -> "Schwarz"
                'R' -> "Rot"
                'G' -> "Grün"
                'C' -> "Farblos"
                else -> null
            }
        }.joinToString(" · ")
    }
}
