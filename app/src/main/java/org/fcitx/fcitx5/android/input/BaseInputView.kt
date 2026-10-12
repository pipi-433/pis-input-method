/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024-2025 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.view.View
import android.view.WindowInsets
import android.widget.PopupMenu
import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.pis.PisGlossary
import org.fcitx.fcitx5.android.pis.PisCandidateMeaningDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.text.InputType
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.data.theme.ThemePrefs
import org.fcitx.fcitx5.android.utils.item
import org.fcitx.fcitx5.android.utils.navbarFrameHeight
import splitties.resources.styledColor
import splitties.views.dsl.core.withTheme
import kotlin.math.max

abstract class BaseInputView(
    val service: FcitxInputMethodService,
    val fcitx: FcitxConnection,
    val theme: Theme
) : ConstraintLayout(service) {

    /**
     * Update UI (from cached events in FcitxAPI) to match fcitx's state, before ready to receive real events
     */
    protected abstract fun onStartHandleFcitxEvent()

    protected abstract fun handleFcitxEvent(it: FcitxEvent<*>)

    private var eventHandlerJob: Job? = null

    private fun setupFcitxEventHandler() {
        eventHandlerJob = service.lifecycleScope.launch {
            fcitx.runImmediately { eventFlow }.collect {
                handleFcitxEvent(it)
            }
        }
    }

    var handleEvents = false
        set(value) {
            field = value
            if (field) {
                onStartHandleFcitxEvent()
                if (eventHandlerJob == null) {
                    setupFcitxEventHandler()
                }
            } else {
                eventHandlerJob?.cancel()
                eventHandlerJob = null
            }
        }

    private fun triggerCandidateAction(idx: Int, actionIdx: Int) {
        fcitx.runIfReady { triggerCandidateAction(idx, actionIdx) }
    }

    private var candidateActionMenu: PopupMenu? = null

    val themedContext = context.withTheme(R.style.Theme_InputViewTheme)
    private var meaningDialog: PisCandidateMeaningDialog? = null
    private var meaningQuery: Job? = null
    private var englishQuery: Job? = null
    private var englishEpoch = 0L

    fun candidateEnglishAction(idx: Int, word: CandidateWord): (() -> Unit)? {
        val epoch = ++englishEpoch
        englishQuery?.cancel()
        val editor = service.currentInputConnection ?: return null
        val info = service.currentInputEditorInfo ?: return null
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val password = if ((info.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER)
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        else variation in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        if (password) return null
        val state = fcitx.runImmediately { candidateListCached }
        val start = service.currentInputSelection.start
        val end = service.currentInputSelection.end
        fun current() = epoch == englishEpoch && isAttachedToWindow && isShown && windowVisibility == View.VISIBLE && editor === service.currentInputConnection &&
            info === service.currentInputEditorInfo && start == service.currentInputSelection.start && end == service.currentInputSelection.end &&
            fcitx.runImmediately { candidateListCached === state }
        return action@{
            if (!current()) return@action
            englishQuery?.cancel()
            englishQuery = service.lifecycleScope.launch {
                val english = PisGlossary.englishTranslation(context.applicationContext, word.text, word.comment) ?: return@launch
                if (!current()) return@launch
                service.postFcitxJob {
                    val candidate = getCandidates(idx, 1).firstOrNull()
                    if (candidateListCached !== state || candidate?.text != word.text || candidate.comment != word.comment) return@postFcitxJob
                    withContext(Dispatchers.Main.immediate) {
                        if (current()) {
                            // Replace the Android composing range before native reset emits empty preedit.
                            val accepted = service.commitCandidateEnglish(english)
                            if (accepted) reset()
                            service.emitPisFeedback(org.fcitx.fcitx5.android.pis.feedback.PisFeedbackEvent.Pulse(
                                if (accepted) org.fcitx.fcitx5.android.pis.feedback.PisFeedbackEvent.Kind.Confirm
                                else org.fcitx.fcitx5.android.pis.feedback.PisFeedbackEvent.Kind.Error))
                        }
                    }
                }.join()
            }
        }
    }
    fun showCandidateMeaning(idx: Int, word: CandidateWord, anchor: View) {
        englishEpoch++; englishQuery?.cancel()
        meaningDialog?.dismiss(); meaningQuery?.cancel()
        val connection = service.currentInputConnection
        InputFeedbacks.hapticFeedback(anchor,longPress = true)
        val dialog = PisCandidateMeaningDialog(service, theme, word.text) {
            service.lifecycleScope.launch {
                val current = fcitx.runOnReady { getCandidates(idx,1).firstOrNull() }
                if (service.currentInputConnection === connection && current?.text == word.text) showCandidateActionMenu(idx,word.text,anchor)
            }
        }
        meaningDialog = dialog
        dialog.onClosed = {
            if (meaningDialog === dialog) {
                meaningQuery?.cancel(); meaningQuery = null; meaningDialog = null
            }
        }
        service.showDialog(dialog)
        meaningQuery = service.lifecycleScope.launch {
            val entry = PisGlossary.lookupLegacy(context.applicationContext,word.text,word.comment)
            if (dialog.isShowing && connection === service.currentInputConnection) {
                dialog.setDefinition(entry.definition)
                service.emitPisFeedback(org.fcitx.fcitx5.android.pis.feedback.PisFeedbackEvent.Pulse(
                    org.fcitx.fcitx5.android.pis.feedback.PisFeedbackEvent.Kind.Confirm))
            }
            else dialog.dismiss()
        }
    }

    fun showCandidateActionMenu(idx: Int, text: String, view: View) {
        candidateActionMenu?.dismiss()
        candidateActionMenu = null
        service.lifecycleScope.launch {
            val actions = fcitx.runOnReady { getCandidateActions(idx) }
            if (actions.isEmpty()) return@launch
            InputFeedbacks.hapticFeedback(view, longPress = true)
            candidateActionMenu = PopupMenu(themedContext, view).apply {
                menu.add(buildSpannedString {
                    bold {
                        color(context.styledColor(android.R.attr.colorAccent)) {
                            append(text)
                        }
                    }
                }).apply {
                    isEnabled = false
                }
                actions.forEach { action ->
                    menu.item(action.text) {
                        triggerCandidateAction(idx, action.id)
                    }
                }
                setOnDismissListener {
                    candidateActionMenu = null
                }
                show()
            }
        }
    }

    private val navbarBackground by ThemeManager.prefs.navbarBackground

    protected fun getNavBarBottomInset(windowInsets: WindowInsets): Int {
        if (navbarBackground != ThemePrefs.NavbarBackground.Full) {
            return 0
        }
        val insets = WindowInsetsCompat.toWindowInsetsCompat(windowInsets)
        // use navigation bar insets when available
        val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        // in case navigation bar insets goes wrong (eg. on LineageOS 21+ with gesture navigation)
        // use mandatory system gesture insets
        val mandatory = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
        var insetsBottom = max(navBars.bottom, mandatory.bottom)
        if (insetsBottom <= 0) {
            // check system gesture insets and fallback to navigation_bar_frame_height just in case
            val gesturesBottom = insets.getInsets(WindowInsetsCompat.Type.systemGestures()).bottom
            if (gesturesBottom > 0) {
                insetsBottom = max(gesturesBottom, context.navbarFrameHeight())
            }
        }
        return insetsBottom
    }

    private val ignoreSystemWindowInsets by AppPrefs.getInstance().advanced.ignoreSystemWindowInsets

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (ignoreSystemWindowInsets) {
            // suppress view's own onApplyWindowInsets
            setOnApplyWindowInsetsListener { _, insets -> insets }
        } else {
            // on API 35+, we must call requestApplyInsets() manually after replacing views,
            // otherwise View#onApplyWindowInsets won't be called. ¯\_(ツ)_/¯
            requestApplyInsets()
        }
    }

    override fun onDetachedFromWindow() {
        englishEpoch++
        englishQuery?.cancel()
        meaningQuery?.cancel(); meaningDialog?.dismiss()
        handleEvents = false
        super.onDetachedFromWindow()
    }
}
