/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android

import org.junit.Assert.*
import org.junit.Test
import org.fcitx.fcitx5.android.pis.PisCandidateEnglish
import android.view.inputmethod.InputConnection
import java.lang.reflect.Proxy

class CandidateEnglishTest {
    @Test fun rejectedEditorPreservesStateAndAcceptedEditorUpdatesItAfterCommit() {
        for (accepted in listOf(false, true)) {
            val calls = mutableListOf<String>()
            val editor = Proxy.newProxyInstance(InputConnection::class.java.classLoader,
                arrayOf(InputConnection::class.java)) { _, method, args ->
                when (method.name) {
                    "beginBatchEdit" -> { calls += "begin"; true }
                    "commitText" -> { assertEquals("technology", args!![0]); assertEquals(1, args[1]); calls += "commit"; accepted }
                    "endBatchEdit" -> { calls += "end"; true }
                    else -> null
                }
            } as InputConnection
            assertEquals(accepted, PisCandidateEnglish.commitToEditor(editor, "technology") { calls += "update-state" })
            assertEquals(if (accepted) listOf("begin", "commit", "update-state", "end") else listOf("begin", "commit", "end"), calls)
        }
    }

    @Test fun failingEditorAlwaysEndsBatchWithoutUpdatingState() {
        val calls = mutableListOf<String>()
        val editor = Proxy.newProxyInstance(InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java)) { _, method, _ ->
            when (method.name) {
                "beginBatchEdit" -> { calls += "begin"; true }
                "commitText" -> throw IllegalStateException("editor closed")
                "endBatchEdit" -> { calls += "end"; true }
                else -> null
            }
        } as InputConnection
        try {
            PisCandidateEnglish.commitToEditor(editor, "technology") { calls += "update-state" }
            fail("Closed editor should fail")
        } catch (_: IllegalStateException) { }
        assertEquals(listOf("begin", "end"), calls)
    }

    @Test fun acceptEnglishPhrasesButNeverChineseDefinitionsOrMissingHints() {
        assertEquals("technology", PisCandidateEnglish.fromHint(" technology "))
        assertEquals("cardinal number", PisCandidateEnglish.fromHint("cardinal number"))
        assertEquals("to build", PisCandidateEnglish.fromHint("to build"))
        assertEquals("café", PisCandidateEnglish.fromHint("café"))
        for (hint in listOf("", "   ", "技术", "本地词库暂无释义", "English 中文", "2026", "[missing]", "CL:", "n."))
            assertNull(hint, PisCandidateEnglish.fromHint(hint))
    }

    @Test fun upwardGestureHasDistanceAndAxisRequirements() {
        assertTrue(PisCandidateEnglish.isUpward(2f, -30f, 24f))
        assertTrue(PisCandidateEnglish.isUpward(0f, -24f, 24f))
        assertFalse(PisCandidateEnglish.isUpward(0f, -23f, 24f))
        assertFalse(PisCandidateEnglish.isUpward(0f, 30f, 24f))
        assertFalse(PisCandidateEnglish.isUpward(30f, -30f, 24f))
        assertFalse(PisCandidateEnglish.isUpward(45f, -30f, 24f))
    }
}
