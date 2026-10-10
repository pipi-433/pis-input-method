/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates

import android.content.Context
import android.view.View
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.text.TextUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.pis.PisGlossary
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.AutoScaleTextView
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView
import org.fcitx.fcitx5.android.utils.pressHighlightDrawable
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import kotlin.math.ceil

class CandidateItemUi(override val ctx: Context, val theme: Theme) : Ui {
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var query: Job? = null
    private var bound: CandidateWord = CandidateWord.Empty
    private var generation = 0
    private fun dp(value: Int) = (value*ctx.resources.displayMetrics.density).toInt()
    private val hint = TextView(ctx).apply {
        textSize = 11f; setSingleLine(true); ellipsize = TextUtils.TruncateAt.END; maxWidth = dp(144)
        gravity = Gravity.CENTER
        setTextColor(if (theme.isDark) 0xffc3c3c3.toInt() else 0xff727780.toInt())
    }

    private val text = view(::AutoScaleTextView) {
        scaleMode = AutoScaleTextView.Mode.Proportional
        textSize = 18f // sp; slightly larger for phone readability.
        isSingleLine = true
        gravity = gravityCenter
        setTextColor(theme.candidateTextColor)
    }

    override val root = view(::CustomGestureView) {
        background = pressHighlightDrawable(theme.keyPressHighlightColor)

        /**
         * candidate long press feedback is handled by [org.fcitx.fcitx5.android.input.BaseInputView.showCandidateActionMenu]
         */
        longPressFeedbackEnabled = false

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(0,dp(8),0,0)
            addView(hint,LinearLayout.LayoutParams(wrapContent,dp(18)))
            addView(text,LinearLayout.LayoutParams(wrapContent,dp(32)))
        }
        add(column, lParams(wrapContent, matchParent) {
            gravity = gravityCenter
        })
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) { loadHint() }
            override fun onViewDetachedFromWindow(view: View) { generation++; query?.cancel() }
        })
    }
    private fun loadHint() {
        query?.cancel(); val token = ++generation; val candidate = bound
        if (candidate.text.isBlank()) return
        PisGlossary.cachedLegacyHint(candidate.text,candidate.comment)?.let {
            if (hint.text.toString() != it) hint.text = it
            return
        }
        query = scope.launch {
            // A short hint needs only the old bilingual index, not full WordNet definitions.
            val entry = PisGlossary.lookupLegacy(ctx.applicationContext,candidate.text,candidate.comment)
            if (token == generation && bound == candidate && hint.text.toString() != entry.hint) hint.text = entry.hint
        }
    }

    fun updateCandidate(candidate: CandidateWord) {
        bound = candidate
        text.textSize = if (candidate.text.any { it in '\u3400'..'\u9fff' || it in '\uf900'..'\ufaff' }) 18f else 19f
        val fg = theme.candidateTextColor
        val altFg = theme.candidateCommentColor
        text.text = buildSpannedString {
            color(fg) {
                append(candidate.text)
            }
            if (candidate.comment.isNotBlank()) {
                if (candidate.spaceBetweenComment) {
                    append(" ")
                }
                color(altFg) {
                    append(candidate.comment)
                }
            }
        }
        // Reserve the hint line from the primary word's size, before asynchronous lookup.
        // A late English hint must not widen the chip and push all following candidates sideways.
        val hintWidth = maxOf(dp(48),ceil(text.paint.measureText(text.text.toString())).toInt()).coerceAtMost(dp(144))
        if (hint.minWidth != hintWidth) hint.minWidth = hintWidth
        if (hint.maxWidth != hintWidth) hint.maxWidth = hintWidth
        val cached = PisGlossary.cachedLegacyHint(candidate.text,candidate.comment).orEmpty()
        if (hint.text.toString() != cached) hint.text = cached
        loadHint()
    }
}
