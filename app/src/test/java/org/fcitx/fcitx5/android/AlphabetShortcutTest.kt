/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.input.keyboard.AlphabetKey
import org.fcitx.fcitx5.android.input.keyboard.KeyAction
import org.fcitx.fcitx5.android.input.keyboard.KeyDef
import org.fcitx.fcitx5.android.input.keyboard.SwipeSymbolDirection
import org.fcitx.fcitx5.android.pis.PisKeyboardLayout
import org.junit.Assert.*
import org.junit.Test

class AlphabetShortcutTest {
    @Test fun allLettersUseUpSwipeAndKeepLongPressShortcuts() {
        val letters = PisKeyboardLayout.text.flatten().filterIsInstance<AlphabetKey>()
        assertEquals(('A'..'Z').map { it.toString() }.toSet(), letters.map { it.character }.toSet())
        letters.forEach { key ->
            val swipe = key.behaviors.filterIsInstance<KeyDef.Behavior.Swipe>().single()
            val hold = key.behaviors.filterIsInstance<KeyDef.Behavior.LongPress>().single()
            assertEquals(key.character, SwipeSymbolDirection.Up, swipe.direction)
            assertTrue(swipe.direction!!.checkY(-1))
            assertFalse(swipe.direction!!.checkY(1))
            assertFalse(swipe.direction!!.checkY(0))
            assertEquals(key.punctuation, (swipe.action as KeyAction.CommitAction).text)
            assertEquals(key.punctuation, (hold.action as KeyAction.CommitAction).text)
        }
    }
}
