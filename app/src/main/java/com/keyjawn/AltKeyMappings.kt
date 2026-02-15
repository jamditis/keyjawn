package com.keyjawn

object AltKeyMappings {

    private val mappings = mapOf(
        // Number row -> shifted symbols
        "1" to listOf("!"),
        "2" to listOf("@"),
        "3" to listOf("#"),
        "4" to listOf("$"),
        "5" to listOf("%"),
        "6" to listOf("^"),
        "7" to listOf("&"),
        "8" to listOf("*"),
        "9" to listOf("("),
        "0" to listOf(")"),

        // Vowels with accents
        "a" to listOf("\u00e1", "\u00e0", "\u00e2", "\u00e4", "\u00e5", "\u00e6"),
        "e" to listOf("\u00e9", "\u00e8", "\u00ea", "\u00eb"),
        "i" to listOf("\u00ed", "\u00ec", "\u00ee", "\u00ef"),
        "o" to listOf("\u00f3", "\u00f2", "\u00f4", "\u00f6", "\u00f8"),
        "u" to listOf("\u00fa", "\u00f9", "\u00fb", "\u00fc"),

        // Common letters
        "n" to listOf("\u00f1"),
        "c" to listOf("\u00e7"),
        "s" to listOf("\u00df", "$"),
        "y" to listOf("\u00ff"),

        // Punctuation
        "." to listOf("\u2026", "?", "!"),
        "-" to listOf("\u2014", "\u2013")
    )

    fun getAlts(label: String): List<String>? {
        val lower = label.lowercase()
        val alts = mappings[lower] ?: return null
        return if (label != lower) {
            // Uppercase: uppercase the alt chars
            alts.map { it.uppercase() }
        } else {
            alts
        }
    }
}
