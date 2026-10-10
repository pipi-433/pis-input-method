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
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView
import org.fcitx.fcitx5.android.utils.pressHighlightDrawable
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter

class CandidateItemUi(override val ctx: Context, val theme: Theme) : Ui {
    val maximumWidth = dp(AppPrefs.getInstance().keyboard.candidateMaxWidth.getValue().coerceIn(80, 480))
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var query: Job? = null
    private var bound: CandidateWord = CandidateWord.Empty
    private var generation = 0
    private fun dp(value: Int) = (value*ctx.resources.displayMetrics.density).toInt()
    private val hint = TextView(ctx).apply {
        textSize = 11f; setSingleLine(true); ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(2), 0, dp(2), 0)
        gravity = Gravity.CENTER
        setTextColor(if (theme.isDark) 0xffc3c3c3.toInt() else 0xff727780.toInt())
    }

    private val text = view(::TextView) {
        textSize = 18f // sp; slightly larger for phone readability.
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(2), 0, dp(2), 0)
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
            minimumWidth = dp(48)
            setPadding(0,dp(8),0,0)
            addView(hint,LinearLayout.LayoutParams(wrapContent,dp(18)))
            addView(text,LinearLayout.LayoutParams(wrapContent,dp(32)))
        }
        add(column, lParams(wrapContent, matchParent) {
            gravity = gravityCenter
        })
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) { updateWidth(); loadHint() }
            override fun onViewDetachedFromWindow(view: View) { generation++; query?.cancel() }
        })
    }
    private fun updateWidth() {
        // Both lines determine the content width; the user limit includes outer padding.
        val contentLimit = (maximumWidth - root.paddingLeft - root.paddingRight).coerceAtLeast(1)
        // Wrap-content measures the wider line and still respects expanded-grid constraints.
        if (hint.maxWidth != contentLimit) hint.maxWidth = contentLimit
        if (text.maxWidth != contentLimit) text.maxWidth = contentLimit
    }

    private fun updateHint(value: String) {
        if (hint.text.toString() != value) hint.text = value
        updateWidth()
    }
    private fun loadHint() {
        query?.cancel(); val token = ++generation; val candidate = bound
        if (candidate.text.isBlank()) return
        PisGlossary.cachedLegacyHint(candidate.text,candidate.comment)?.let {
            updateHint(it)
            return
        }
        query = scope.launch {
            // A short hint needs only the old bilingual index, not full WordNet definitions.
            val entry = PisGlossary.lookupLegacy(ctx.applicationContext,candidate.text,candidate.comment)
            if (token == generation && bound == candidate) updateHint(entry.hint)
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
        val cached = PisGlossary.cachedLegacyHint(candidate.text,candidate.comment).orEmpty()
        updateHint(cached)
        loadHint()
    }
}
