package com.keyjawn

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KeyboardHapticsTest {

    private data class Event(val type: Int, val flags: Int)

    private class RecordingView(context: Context) : View(context) {
        val events = mutableListOf<Event>()

        override fun performHapticFeedback(feedbackConstant: Int): Boolean {
            events += Event(feedbackConstant, 0)
            return true
        }

        override fun performHapticFeedback(feedbackConstant: Int, flags: Int): Boolean {
            events += Event(feedbackConstant, flags)
            return true
        }
    }

    @Test
    fun `enter uses confirm while a character stays keyboard tap`() {
        val view = RecordingView(RuntimeEnvironment.getApplication())
        val haptics = KeyboardHaptics(view, enabled = { true }, sdkInt = 33)

        haptics.key(KeyOutput.Character("a"))
        haptics.key(KeyOutput.Enter)

        assertEquals(
            listOf(
                Event(HapticFeedbackConstants.KEYBOARD_TAP, 0),
                Event(HapticFeedbackConstants.CONFIRM, 0)
            ),
            view.events
        )
    }

    @Test
    fun `enter falls back to keyboard tap below api 30`() {
        val view = RecordingView(RuntimeEnvironment.getApplication())
        val haptics = KeyboardHaptics(view, enabled = { true }, sdkInt = 29)

        haptics.key(KeyOutput.Enter)

        assertEquals(listOf(Event(HapticFeedbackConstants.KEYBOARD_TAP, 0)), view.events)
    }

    @Test
    fun `repeat press ignores only the view setting`() {
        val view = RecordingView(RuntimeEnvironment.getApplication())
        val haptics = KeyboardHaptics(view, enabled = { true }, sdkInt = 33)

        haptics.repeatPress()

        assertEquals(
            listOf(
                Event(
                    HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                )
            ),
            view.events
        )
    }

    @Test
    fun `confirm style pick uses context click`() {
        val view = RecordingView(RuntimeEnvironment.getApplication())
        val haptics = KeyboardHaptics(view, enabled = { true }, sdkInt = 33)

        haptics.confirm()

        assertEquals(listOf(Event(HapticFeedbackConstants.CONTEXT_CLICK, 0)), view.events)
    }

    @Test
    fun `disabled haptics suppress every semantic event`() {
        val view = RecordingView(RuntimeEnvironment.getApplication())
        val haptics = KeyboardHaptics(view, enabled = { false }, sdkInt = 33)

        haptics.key(KeyOutput.Enter)
        haptics.repeatPress()
        haptics.confirm()
        haptics.perform(HapticFeedbackConstants.LONG_PRESS)

        assertTrue(view.events.isEmpty())
    }
}
