/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis

import android.os.Handler
import android.os.Looper
import android.view.inputmethod.InputConnection
import android.widget.Toast
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.pis.translation.EditorSnapshot
import org.fcitx.fcitx5.android.pis.translation.LocalTranslationClient
import org.fcitx.fcitx5.android.pis.translation.ModelStore
import org.fcitx.fcitx5.android.pis.feedback.PisFeedbackEvent
import org.fcitx.fcitx5.android.pis.feedback.PisFeedbackEvent.Phase
import java.util.concurrent.Executors

/** Request ownership stays in the IME; renderer replacement cannot apply stale text. */
class PisTranslationCoordinator(private val service: FcitxInputMethodService) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var generation = 0
    private var snapshot: EditorSnapshot? = null
    private var connection: InputConnection? = null
    private var text: String? = null
    private var pending = false
    private fun feedback(phase: Phase) = service.emitPisFeedback(PisFeedbackEvent.Task(
        PisFeedbackEvent.TaskKind.Translation, generation.toLong(), phase))
    var renderer: ((String?, (() -> Unit)?) -> Unit)? = null
        set(value) { field = value; render() }
    fun prewarm() {
        if (service.getSystemService(android.os.UserManager::class.java).isUserUnlocked) LocalTranslationClient.prewarm(service)
    }
    fun translate() {
        cancel()
        try {
            val kind = service.currentInputEditorInfo?.inputType ?: 0
            val variation = kind and android.text.InputType.TYPE_MASK_VARIATION
            val password = if ((kind and android.text.InputType.TYPE_MASK_CLASS) == android.text.InputType.TYPE_CLASS_NUMBER) variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                else variation in listOf(android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD, android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
            if (password) throw IllegalArgumentException("密码输入框不提供翻译")
            val editor = service.currentInputConnection ?: return
            val captured = EditorSnapshot.capture(editor) ?: throw IllegalArgumentException("这个输入框无法读取文字")
            val source = captured.source()
            if (source.isBlank()) throw IllegalArgumentException("输入框没有文字")
            snapshot = captured; connection = editor; pending = true
            feedback(Phase.Preparing)
            text = if (ModelStore.present(service)) "正在翻译为英文…" else "正在准备离线模型并翻译…"
            render()
            val token = generation
            worker.execute {
                val result = runCatching { LocalTranslationClient.translateToEnglish(service, source) }
                main.post {
                    if (token != generation || editor !== service.currentInputConnection) return@post
                    pending = false
                    if (!captured.matches(editor)) { cancel(); return@post }
                    result.onSuccess { text = it; render(); feedback(Phase.Ready) }.onFailure {
                        feedback(Phase.Error)
                        snapshot = null; text = it.message ?: "翻译失败，请重试"; render()
                    }
                }
            }
        } catch (error: IllegalArgumentException) {
            service.emitPisFeedback(PisFeedbackEvent.Pulse(PisFeedbackEvent.Kind.Error))
            text = error.message; render()
        }
    }
    private fun apply() {
        val editor = connection
        val captured = snapshot
        val translated = text
        if (pending || editor == null || captured == null || translated == null) return
        if (editor !== service.currentInputConnection || !captured.matches(editor)) {
            cancel(); Toast.makeText(service, "原文已经变化，请重新翻译", Toast.LENGTH_SHORT).show(); return
        }
        editor.beginBatchEdit()
        var accepted = false
        try {
            editor.finishComposingText()
            if (!captured.selectedRange() && !editor.performContextMenuAction(android.R.id.selectAll)) editor.setSelection(0, captured.text.length)
            accepted = editor.commitText(translated, 1)
        } finally { editor.endBatchEdit() }
        cancel()
        service.emitPisFeedback(PisFeedbackEvent.Pulse(if (accepted) PisFeedbackEvent.Kind.Confirm else PisFeedbackEvent.Kind.Error))
    }
    private fun render() {
        renderer?.invoke(text, if (!pending && snapshot != null) ({ apply() }) else null)
    }
    fun cancel() {
        // Still invalidate queued results, but idle typing needs no IPC or bar relayout.
        if (!pending && snapshot == null && connection == null && text == null) { generation++; return }
        if (pending || snapshot != null) feedback(Phase.Canceled)
        generation++; LocalTranslationClient.cancel(); text = null; snapshot = null; connection = null; pending = false; render()
    }
    fun close() { cancel(); renderer = null; worker.shutdownNow(); LocalTranslationClient.release() }
}
