/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.pis.translation.EditorSnapshot
import org.fcitx.fcitx5.android.pis.feedback.PisFeedbackEvent
import org.fcitx.fcitx5.android.pis.feedback.PisFeedbackEvent.Phase

class PisVoiceCoordinator(private val service: FcitxInputMethodService) {
    private val main = Handler(Looper.getMainLooper())
    private val engine = PisVoiceRuntime.engine(service.applicationContext)
    private class Session(val connection: InputConnection, val snapshot: VoicePostProcessor.Snapshot,
                          val originalSelection: String, var expected: EditorSnapshot, val mayLearn: Boolean, val token: Long) {
        val capture = PisVoiceEngine.Capture()
        var raw = ""; var hasDraft = false; var state = 0 // recording / processing / candidates
        var items: List<VoicePostProcessor.Candidate> = emptyList()
        var deadline: Runnable? = null; var timeout: Runnable? = null
    }
    private var active: Session? = null
    private var generation = 0L
    private fun feedback(s: Session, phase: Phase) = service.emitPisFeedback(PisFeedbackEvent.Task(
        PisFeedbackEvent.TaskKind.Voice, s.token, phase))
    private var label: String? = null
    var renderer: ((String?, List<VoicePostProcessor.Candidate>, ((Int) -> Unit)?, (() -> Unit)?) -> Unit)? = null
        set(value) { field = value; render() }
    fun prepare() {
        PisVoiceRuntime.prepare(service)
    }
    private fun learningAllowed() = org.fcitx.fcitx5.android.pis.PisInputOptions.learningAllowed(service,service.currentInputEditorInfo)
    private fun current(s: Session) = active === s && s.connection === service.currentInputConnection && !s.capture.canceled.get()
    fun start() {
        cancel(false)
        if (!service.getSystemService(android.os.UserManager::class.java).isUserUnlocked) { label = "解锁后可使用本地语音"; render(); return }
        val kind = service.currentInputEditorInfo?.inputType ?: 0
        val variation = kind and android.text.InputType.TYPE_MASK_VARIATION
        val password = if ((kind and android.text.InputType.TYPE_MASK_CLASS) == android.text.InputType.TYPE_CLASS_NUMBER) variation == 16 else variation in listOf(128, 144, 224)
        if (password || service.currentInputEditorInfo?.privateImeOptions?.contains("noMicrophoneKey") == true) {
            label = "当前输入框不提供语音"; render(); return
        }
        if (service.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            service.startActivity(Intent(service, PisVoicePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val connection = service.currentInputConnection ?: return
        val expected = EditorSnapshot.capture(connection)
        if (expected == null) { label = "这个输入框无法安全接收语音"; render(); return }
        val snapshot = VoicePipeline.snapshot(service)
        val s = Session(connection, snapshot, connection.getSelectedText(0)?.toString().orEmpty(), expected, learningAllowed() && snapshot.learn, ++generation)
        active = s; label = if (engine.ready) "正在聆听…松开结束，上滑取消" else "正在准备语音模型…"; render()
        feedback(s, Phase.Preparing)
        engine.start(s.capture, snapshot,
            { text -> main.post { if (current(s)) updateDraft(s, text) } },
            { text -> main.post { if (current(s)) process(s, text) } },
            { error -> main.post { if (current(s)) {
                cancel(false); label = error; render()
                service.emitPisFeedback(PisFeedbackEvent.Pulse(PisFeedbackEvent.Kind.Error))
            } } },
            started = { main.post { if (current(s) && !s.capture.stopped.get()) {
                feedback(s, Phase.Listening)
                label = "正在聆听…松开结束，上滑取消"; render()
            } } },
            level = { amplitude -> main.post { if (current(s) && !s.capture.stopped.get()) {
                service.emitPisFeedback(PisFeedbackEvent.VoiceLevel(s.token, amplitude))
            } } })
    }
    fun stop(canceled: Boolean) {
        val s = active ?: return
        if (canceled) { cancel(true); return }
        if (s.state != 0) return
        if (!engine.ready) { cancel(false); label = "模型准备中，请稍后再长按空格"; render(); return }
        s.capture.stop(false)
        feedback(s, Phase.Processing)
        label = "正在处理语音…"; render()
    }
    private fun updateDraft(s: Session, text: String): Boolean {
        if (!current(s) || !s.expected.matches(s.connection)) { cancel(false); return false }
        if (text.isNotEmpty() && text != s.raw) {
            if (!s.connection.setComposingText(text, 1)) { cancel(false); return false }
            s.hasDraft = true; s.raw = text
            s.expected = EditorSnapshot.capture(s.connection) ?: run { cancel(false); return false }
        }
        label = if (text.isBlank()) "正在聆听…松开结束，上滑取消" else "🎙 $text"; render(); return true
    }
    private fun process(s: Session, text: String) {
        if (text.isBlank()) { cancel(false); label = "没有听到语音，请重试"; render(); return }
        if (!updateDraft(s, text)) return
        s.state = 1; label = "正在处理标点…"; render()
        feedback(s, Phase.Processing)
        s.deadline = Runnable { if (current(s) && s.state == 1) showCandidates(s, listOf(VoicePostProcessor.Candidate(s.raw, "原文", 0.0))) }
        main.postDelayed(s.deadline!!, 2000)
        try { VoicePipeline.WORK.execute {
            val items = runCatching { VoicePipeline.process(text, s.snapshot) }.getOrElse { listOf(VoicePostProcessor.Candidate(text, "原文", 0.0)) }
            main.post { if (current(s) && s.state == 1) showCandidates(s, items) }
        } } catch (_: java.util.concurrent.RejectedExecutionException) { showCandidates(s, listOf(VoicePostProcessor.Candidate(text, "原文", 0.0))) }
    }
    private fun showCandidates(s: Session, items: List<VoicePostProcessor.Candidate>) {
        s.deadline?.let(main::removeCallbacks)
        s.items = items; s.state = 2; label = null
        feedback(s, Phase.Ready)
        if (!s.snapshot.candidates) { commit(s, items.first().text); return }
        render()
        s.timeout = Runnable { if (current(s)) commit(s, s.items.first().text) }
        main.postDelayed(s.timeout!!, s.snapshot.timeout * 1000L)
    }
    private fun commit(s: Session, text: String) {
        if (!current(s) || !s.expected.matches(s.connection)) { cancel(false); return }
        val accepted = s.connection.commitText(text, 1)
        if (accepted) s.connection.finishComposingText()
        val learn = accepted && s.mayLearn && learningAllowed()
        cleanup(s)
        feedback(s, Phase.Canceled)
        service.emitPisFeedback(PisFeedbackEvent.Pulse(if (accepted) PisFeedbackEvent.Kind.Confirm else PisFeedbackEvent.Kind.Error))
        if (learn) try { VoicePipeline.WORK.execute { VoicePipeline.store(service).record(text) } }
        catch (_: java.util.concurrent.RejectedExecutionException) { /* A busy learning queue must not block input. */ }
    }
    fun beforeKey() {
        val s = active ?: run {
            if (label != null) { label = null; render() }
            return
        }
        if (s.state == 2) commit(s, s.items.first().text) else cancel(false)
    }
    fun hide() {
        val s = active ?: run { label = null; render(); return }
        if (s.state == 2) commit(s, s.items.first().text) else cancel(false)
    }
    fun onEditorSelectionChanged() {
        val s = active ?: return
        if (!s.hasDraft) return
        // Selection moves and host edits end voice ownership before another IME composition starts.
        if (!current(s) || !s.expected.matches(s.connection)) cancel(false)
    }
    fun cancel(erase: Boolean) {
        active?.let { s ->
            feedback(s, Phase.Canceled)
            val valid = current(s) && s.expected.matches(s.connection)
            s.capture.stop(true)
            if (s.hasDraft && s.connection === service.currentInputConnection) {
                if (erase && valid) s.connection.setComposingText(s.originalSelection, 1)
                // Releasing a stale composing span is safe; restoring stale text is not.
                s.connection.finishComposingText()
            }
            cleanup(s)
        }
        label = null; render()
    }
    private fun cleanup(s: Session) {
        s.capture.stop(true); s.deadline?.let(main::removeCallbacks); s.timeout?.let(main::removeCallbacks)
        if (active === s) active = null
        label = null; render()
    }
    private fun render() {
        val s = active
        renderer?.invoke(label, if (s?.state == 2) s.items else emptyList(),
            if (s?.state == 2) ({ index -> s.items.getOrNull(index)?.let { commit(s, it.text) } }) else null,
            if (s != null) ({ cancel(true) }) else null)
    }
    fun close() { cancel(false); renderer = null }
}
