/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis

import android.view.inputmethod.InputConnection
import org.fcitx.fcitx5.android.pis.translation.EditorSnapshot
import kotlin.math.abs

/** Per-gesture undo only. Reject changed editors and never retain deleted text after release. */
class PisBackspaceSession(private val connection: InputConnection, private val left: String) {
    private var expected = EditorSnapshot.capture(connection)
    private val available = left.codePointCount(0,left.length).coerceAtMost(512)
    private var deleted = 0
    private var keyDeletes = 0
    private var finished = false
    private fun valid(current: InputConnection?) = !finished && current === connection && expected?.matches(connection) == true
    private fun index(count: Int) = left.offsetByCodePoints(left.length,-count)
    fun step(current: InputConnection?): Boolean {
        if (!valid(current)) { finish(); return false }
        if (!moveTo(deleted + 1,current)) return false
        keyDeletes = deleted
        return true
    }
    fun move(dx: Float, density: Float, current: InputConnection?): Boolean {
        if (!valid(current)) { finish(); return false }
        if (dx >= 12*density) return moveTo(0,current)
        val extra = if (abs(dx) < 24*density) 0 else ((abs(dx)-24*density)/(12*density)).toInt()+1
        return moveTo((keyDeletes + if (dx < 0) extra else -extra).coerceIn(0,available),current)
    }
    private fun moveTo(target: Int,current: InputConnection?): Boolean {
        if (!valid(current)) return false
        val next = target.coerceIn(0,available)
        val accepted = when {
            next > deleted -> connection.deleteSurroundingText(index(deleted)-index(next),0)
            next < deleted -> connection.commitText(left.substring(index(deleted),index(next)),1)
            else -> true
        }
        if (!accepted) { finish(); return false }
        deleted = next
        expected = EditorSnapshot.capture(connection)
        return expected != null
    }
    fun finish() { finished = true; expected = null }
    companion object {
        fun begin(connection: InputConnection?): PisBackspaceSession? {
            if (connection == null || !connection.getSelectedText(0).isNullOrEmpty()) return null
            val left = connection.getTextBeforeCursor(8192,0)?.toString() ?: return null
            if (left.isEmpty()) return null
            return PisBackspaceSession(connection,left).takeIf { it.expected != null }
        }
    }
}
