/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis.feedback

import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.input.keyboard.KeyAction
import kotlin.math.roundToInt

/** Visual events contain positions and task ownership, never editor text or audio. */
sealed interface PisFeedbackEvent {
    enum class Kind { Key, Punctuation, Delete, Space, Enter, Shift, Language, Layout, Confirm, Error }
    enum class TaskKind { Voice, Translation }
    enum class Phase { Preparing, Listening, Processing, Ready, Success, Error, Canceled }
    data class Pulse(val kind: Kind, val row: Int = 0, val column: Int = 5) : PisFeedbackEvent
    data class Language(val chinese: Boolean, val animate: Boolean) : PisFeedbackEvent
    data class Task(val kind: TaskKind, val token: Long, val phase: Phase) : PisFeedbackEvent
    data class VoiceLevel(val token: Long, val level: Float) : PisFeedbackEvent
    data object Reset : PisFeedbackEvent

    companion object {
        fun fromAction(action: KeyAction): Pulse? = when (action) {
            is KeyAction.FcitxKeyAction -> character(action.act)
            is KeyAction.CommitAction -> character(action.text)
            is KeyAction.SymAction -> when (action.sym.sym) {
                FcitxKeyMapping.FcitxKey_BackSpace -> Pulse(Kind.Delete)
                FcitxKeyMapping.FcitxKey_space -> Pulse(Kind.Space)
                FcitxKeyMapping.FcitxKey_Return -> Pulse(Kind.Enter)
                else -> null
            }
            is KeyAction.CapsAction -> Pulse(Kind.Shift, if (action.lock) 1 else 0)
            is KeyAction.PisSweepMoveAction -> Pulse(Kind.Delete)
            is KeyAction.PisClipboardAction -> Pulse(Kind.Punctuation)
            // Language feedback comes from IMChange, after the engine has actually switched.
            else -> null
        }

        private fun character(text: String): Pulse? {
            if (text.length != 1) return null
            val c = text[0].uppercaseChar()
            val rows = arrayOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
            rows.forEachIndexed { row, keys ->
                val index = keys.indexOf(c)
                if (index >= 0) return Pulse(Kind.Key, if (row == 0) 0 else 1,
                    (index * 11f / (keys.length - 1)).roundToInt())
            }
            if (c in '0'..'9') {
                val n = c - '0'
                return Pulse(Kind.Key, if (n in 1..3) 0 else 1,
                    if (n == 0) 5 else 1 + ((n - 1) % 3) * 5)
            }
            return if (c == ' ') Pulse(Kind.Space) else Pulse(Kind.Punctuation)
        }
    }
}
