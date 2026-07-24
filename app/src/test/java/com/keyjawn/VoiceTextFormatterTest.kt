package com.keyjawn

import org.junit.Assert.*
import org.junit.Test

class VoiceTextFormatterTest {

    // ---- Spacing between utterances ----
    // The recognizer returns a bare utterance with no knowledge of what is
    // already in the field, so continuous dictation is where the missing space
    // shows up: "deploy the service" + "then restart it".

    @Test
    fun `appending after a word inserts the separating space`() {
        assertEquals(" world", VoiceTextFormatter.joinWithContext("hello", "world"))
    }

    @Test
    fun `appending into an empty field adds no leading space`() {
        assertEquals("hello", VoiceTextFormatter.joinWithContext("", "hello"))
        assertEquals("hello", VoiceTextFormatter.joinWithContext(null, "hello"))
    }

    @Test
    fun `existing trailing whitespace is not doubled`() {
        assertEquals("world", VoiceTextFormatter.joinWithContext("hello ", "world"))
        assertEquals("world", VoiceTextFormatter.joinWithContext("hello\n", "world"))
    }

    @Test
    fun `punctuation is not pushed away from the word it follows`() {
        assertEquals(".", VoiceTextFormatter.joinWithContext("done", "."))
        assertEquals(", then", VoiceTextFormatter.joinWithContext("done", ", then"))
    }

    @Test
    fun `an open bracket or path separator glues the next utterance on`() {
        // Dictating into "cd ~/" or "run(" should continue the token, not start
        // a new word -- the shell cases this keyboard exists for.
        assertEquals("src", VoiceTextFormatter.joinWithContext("cd ~/", "src"))
        assertEquals("main", VoiceTextFormatter.joinWithContext("run(", "main"))
        assertEquals("verify", VoiceTextFormatter.joinWithContext("--no-", "verify"))
    }

    @Test
    fun `an empty utterance stays empty`() {
        assertEquals("", VoiceTextFormatter.joinWithContext("hello", ""))
    }

    // ---- Spoken commands ----

    @Test
    fun `commands are left alone when the preference is off`() {
        assertEquals(
            "add a new line to the file",
            VoiceTextFormatter.applyCommands("add a new line to the file", enabled = false)
        )
    }

    @Test
    fun `new line becomes a line break`() {
        assertEquals("git status\nls", VoiceTextFormatter.applyCommands("git status new line ls", true))
    }

    @Test
    fun `new paragraph becomes a blank line`() {
        assertEquals("one\n\ntwo", VoiceTextFormatter.applyCommands("one new paragraph two", true))
    }

    @Test
    fun `punctuation attaches to the word before it`() {
        assertEquals("hello, world.", VoiceTextFormatter.applyCommands("hello comma world period", true))
    }

    @Test
    fun `the longest matching phrase wins`() {
        // "exclamation point" must not degrade into the single word "exclamation".
        assertEquals("stop!", VoiceTextFormatter.applyCommands("stop exclamation point", true))
        assertEquals("really?", VoiceTextFormatter.applyCommands("really question mark", true))
    }

    @Test
    fun `brackets hug the token they open`() {
        assertEquals("run(main)", VoiceTextFormatter.applyCommands("run open paren main close paren", true))
    }

    @Test
    fun `commands match regardless of case or trailing punctuation`() {
        // The recognizer capitalizes sentence starts and adds its own full stops.
        assertEquals("\nls", VoiceTextFormatter.applyCommands("New line ls", true))
        assertEquals("a, b", VoiceTextFormatter.applyCommands("a comma. b", true))
    }

    @Test
    fun `plain speech survives command rewriting unchanged`() {
        assertEquals(
            "restart the deploy job",
            VoiceTextFormatter.applyCommands("restart the deploy job", true)
        )
    }

    @Test
    fun `blank input is returned as is`() {
        assertEquals("", VoiceTextFormatter.applyCommands("", true))
        assertEquals("   ", VoiceTextFormatter.applyCommands("   ", true))
    }
}
