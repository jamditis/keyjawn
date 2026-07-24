package com.keyjawn

/**
 * Turns a raw speech-recognition string into the exact text to commit.
 *
 * Two independent jobs, both pure so they can be unit tested without a
 * recognizer or an InputConnection:
 *
 *  1. [applyCommands] rewrites spoken punctuation and layout words ("new line",
 *     "comma") into the characters they name. Off by default -- dictating a
 *     prompt about "adding a new line to the file" must not silently turn into a
 *     line break -- so the caller passes the user's preference through.
 *  2. [joinWithContext] decides whether the committed text needs a leading
 *     space. The recognizer hands back a bare utterance with no knowledge of
 *     what is already in the field, so appending it verbatim across two
 *     dictation rounds produces "hello worldand then". That single missing space
 *     is the most visible seam in continuous dictation.
 */
object VoiceTextFormatter {

    /**
     * Spoken phrase -> replacement, longest phrase first so "exclamation point"
     * is matched before "exclamation". Keys are lowercase and matched against
     * whole words only.
     */
    private val COMMANDS: List<Pair<String, String>> = listOf(
        "new paragraph" to "\n\n",
        "exclamation point" to "!",
        "exclamation mark" to "!",
        "question mark" to "?",
        "open parenthesis" to "(",
        "close parenthesis" to ")",
        "double quote" to "\"",
        "dollar sign" to "$",
        "percent sign" to "%",
        "equals sign" to "=",
        "plus sign" to "+",
        "open paren" to "(",
        "close paren" to ")",
        "open bracket" to "[",
        "close bracket" to "]",
        "open brace" to "{",
        "close brace" to "}",
        "at sign" to "@",
        "full stop" to ".",
        "new line" to "\n",
        "semicolon" to ";",
        "underscore" to "_",
        "ampersand" to "&",
        "asterisk" to "*",
        "backslash" to "\\",
        "backtick" to "`",
        "hashtag" to "#",
        "newline" to "\n",
        "period" to ".",
        "comma" to ",",
        "colon" to ":",
        "slash" to "/",
        "dash" to "-",
        "hyphen" to "-",
        "caret" to "^",
        "tilde" to "~",
        "pipe" to "|"
    )

    /** The longest command phrase, in words. Bounds the lookahead window. */
    private val MAX_PHRASE_WORDS: Int = COMMANDS.maxOf { it.first.count { c -> c == ' ' } + 1 }

    /** Replacements that attach to the preceding word with no space before. */
    private const val HUGS_PREVIOUS = ".,!?;:)]}%"

    /** Replacements that attach to the following word with no space after. */
    private const val HUGS_NEXT = "([{@#$~/\\`"

    /**
     * Characters that never want a space inserted before them when a fresh
     * utterance is appended to existing text.
     */
    private const val NO_SPACE_BEFORE = ".,!?;:)]}'\"%"

    /**
     * Trailing characters in the existing text after which a new utterance is
     * already "open" -- an opening bracket, a path separator, a sigil -- so
     * gluing the next word straight on is what the user meant.
     */
    private const val NO_SPACE_AFTER = "([{@#$~/\\`_-=+"

    /**
     * Rewrites spoken command words in [raw] into the characters they name.
     * Returns [raw] unchanged when [enabled] is false.
     *
     * Punctuation is glued to the word before it ("hello comma there" ->
     * "hello, there") and brackets to the word after ("open paren x close paren"
     * -> "(x)"), so the result reads like typed text rather than a token dump.
     */
    fun applyCommands(raw: String, enabled: Boolean): String {
        if (!enabled || raw.isBlank()) return raw
        val words = raw.trim().split(WHITESPACE)
        val out = StringBuilder()
        // True once a replacement asked the next token to butt straight up
        // against it (an opening bracket, a sigil), so no separator is emitted.
        var suppressSpace = true
        var i = 0
        while (i < words.size) {
            var matched = false
            var take = minOf(MAX_PHRASE_WORDS, words.size - i)
            while (take >= 1 && !matched) {
                val phrase = words.subList(i, i + take).joinToString(" ") { normalize(it) }
                val replacement = lookup(phrase)
                if (replacement != null) {
                    appendReplacement(out, replacement)
                    suppressSpace = replacement.last() in HUGS_NEXT || replacement.last() == '\n'
                    i += take
                    matched = true
                }
                take--
            }
            if (!matched) {
                if (!suppressSpace && out.isNotEmpty()) out.append(' ')
                out.append(words[i])
                suppressSpace = false
                i++
            }
        }
        return out.toString()
    }

    /**
     * The text to actually commit for [addition], given the [textBefore] the
     * cursor already sits after. Adds the single separating space when both
     * sides want one, and nothing otherwise.
     */
    fun joinWithContext(textBefore: CharSequence?, addition: String): String {
        if (addition.isEmpty()) return addition
        if (!needsLeadingSpace(textBefore, addition)) return addition
        return " $addition"
    }

    /** Whether committing [addition] after [textBefore] needs a space between. */
    fun needsLeadingSpace(textBefore: CharSequence?, addition: String): Boolean {
        if (addition.isEmpty()) return false
        val first = addition.first()
        if (first.isWhitespace() || first in NO_SPACE_BEFORE) return false
        if (textBefore.isNullOrEmpty()) return false
        val last = textBefore.last()
        if (last.isWhitespace() || last in NO_SPACE_AFTER) return false
        return true
    }

    /**
     * A replacement never takes a space in front of it. Punctuation belongs to
     * the word it follows ("hello," not "hello ,") and an opening bracket or
     * sigil belongs to the word it introduces ("run(main)" not "run (main)") --
     * which is also what a shell command dictated aloud should come out as.
     */
    private fun appendReplacement(out: StringBuilder, replacement: String) {
        if (replacement.first() == '\n') {
            // A line break swallows the space that would have preceded it.
            while (out.isNotEmpty() && out.last() == ' ') out.setLength(out.length - 1)
        }
        out.append(replacement)
    }

    private fun lookup(phrase: String): String? {
        for ((key, value) in COMMANDS) {
            if (key == phrase) return value
        }
        return null
    }

    /**
     * Lowercases a spoken word and drops the punctuation the recognizer may have
     * attached to it, so "period." and "Period" both match the "period" command.
     */
    private fun normalize(word: String): String =
        word.lowercase().trim { it in HUGS_PREVIOUS || it == '"' || it == '\'' }

    private val WHITESPACE = Regex("\\s+")
}
