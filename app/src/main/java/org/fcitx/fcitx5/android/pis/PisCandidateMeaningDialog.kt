/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.keyboard.radiusDrawable
import org.fcitx.fcitx5.android.input.keyboard.shadowedKeyBackgroundDrawable
import kotlin.math.roundToInt
import java.util.function.Consumer

/** Word definitions use the active keyboard's surfaces, ink and key treatment. */
class PisCandidateMeaningDialog(
    context: Context,
    private val theme: Theme,
    private val word: String,
    onCandidateActions: () -> Unit
) : Dialog(context, if (theme.isDark) R.style.PisMeaningDialog else R.style.PisMeaningDialogLight) {

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
    private val match = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
    private val radius = dp(ThemeManager.prefs.keyRadius.getValue()).toFloat()
    private val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = glassBackground(false)
        foreground = PisGlassBarDrawable(dp(12).toFloat(), resources.displayMetrics.density, theme.isDark)
        clipToOutline = true
    }
    private val blurListener = Consumer<Boolean> { enabled -> updateGlassBlur(enabled) }
    private var observingBlur = false
    private val heading = TextView(context).apply {
        text = "释义"; textSize = 12f
        setTextColor(theme.candidateCommentColor)
    }
    private val definition = TextView(context).apply {
        text = "正在读取本地释义…"; textSize = 16f
        setTextColor(theme.popupTextColor)
        setLineSpacing(dp(3).toFloat(), 1f)
        setTextIsSelectable(true)
        highlightColor = theme.keyPressHighlightColor
    }

    // Dialog dismissal belongs to the IME service. onStop keeps lookup cancellation
    // independent of the service's own setOnDismissListener.
    var onClosed: (() -> Unit)? = null

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setTitle(word)
        window?.setBackgroundDrawable(radiusDrawable(dp(12).toFloat(), Color.TRANSPARENT))
        panel.addView(TextView(context).apply {
            text = "本地词义"; textSize = 12f
            setTextColor(theme.candidateCommentColor)
        }, LinearLayout.LayoutParams(match, wrap))
        panel.addView(TextView(context).apply {
            text = word; textSize = 23f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(theme.candidateTextColor)
        }, LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(6); bottomMargin = dp(14) })
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            background = radiusDrawable(radius, ColorUtils.setAlphaComponent(theme.popupBackgroundColor, 205))
            foreground = PisGlassBarDrawable(radius, resources.displayMetrics.density, theme.isDark)
            addView(heading, LinearLayout.LayoutParams(match, wrap).apply { bottomMargin = dp(8) })
            addView(object : ScrollView(context) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val limit = dp((resources.configuration.screenHeightDp * .36f).toInt().coerceIn(64, 320))
                    val available = if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED)
                        limit else minOf(limit, View.MeasureSpec.getSize(heightMeasureSpec))
                    super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(available, View.MeasureSpec.AT_MOST))
                }
            }.apply {
                isVerticalScrollBarEnabled = true
                addView(definition, ViewGroup.LayoutParams(match, wrap))
            }, LinearLayout.LayoutParams(match, wrap))
        }
        // Give the body the shrinking space on short/landscape windows, keeping actions visible.
        panel.addView(card, LinearLayout.LayoutParams(match, wrap, 1f))
        panel.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(keyButton("候选操作", true) { dismiss(); onCandidateActions() },
                LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(10) })
            addView(keyButton("关闭", false) { dismiss() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        }, LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(14) })
        setContentView(panel)
    }

    private fun keyButton(label: String, function: Boolean, action: () -> Unit) = Button(context).apply {
        text = label; textSize = 15f; isAllCaps = false
        gravity = Gravity.CENTER
        minWidth = 0; minHeight = 0
        setPadding(dp(8), 0, dp(8), 0)
        stateListAnimator = null
        elevation = 0f
        setTextColor(if (function) theme.altKeyTextColor else theme.keyTextColor)
        background = shadowedKeyBackgroundDrawable(
            if (function) theme.altKeyBackgroundColor else theme.keyBackgroundColor,
            theme.keyShadowColor, radius, dp(1), 0, dp(1)
        )
        foreground = RippleDrawable(ColorStateList.valueOf(theme.keyPressHighlightColor), null, radiusDrawable(radius))
        setOnClickListener { action() }
    }

    private fun glassBackground(blur: Boolean) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(
            ColorUtils.setAlphaComponent(ColorUtils.blendARGB(theme.keyboardColor, theme.keyBackgroundColor, .4f), if (blur) 224 else 246),
            ColorUtils.setAlphaComponent(theme.keyboardColor, if (blur) 211 else 238),
            ColorUtils.setAlphaComponent(ColorUtils.blendARGB(theme.keyboardColor, theme.altKeyBackgroundColor, .08f), if (blur) 222 else 246)
        )
    ).apply { cornerRadius = dp(12).toFloat() }

    private fun updateGlassBlur(enabled: Boolean) {
        panel.background = glassBackground(enabled)
        if (Build.VERSION.SDK_INT >= 31) window?.setBackgroundBlurRadius(if (enabled) dp(18) else 0)
    }

    fun setDefinition(value: String) {
        val chinesePrefix = "中文词条\n$word\n\n英文译义\n"
        val englishPrefix = "英文词条\n$word\n\n中文释义与音标\n"
        heading.text = when {
            value.startsWith(chinesePrefix) -> "英文译义"
            value.startsWith(englishPrefix) -> "释义与音标"
            else -> "释义"
        }
        definition.text = value.removePrefix(chinesePrefix).removePrefix(englishPrefix)
            .ifBlank { "本地词库暂无这个词的释义" }
    }

    override fun onStart() {
        super.onStart()
        val width = dp(context.resources.configuration.screenWidthDp - 32).coerceAtMost(dp(420))
        window?.setLayout(width, wrap)
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(WindowManager::class.java)
                .addCrossWindowBlurEnabledListener(context.mainExecutor, blurListener)
            observingBlur = true
        }
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= 31 && observingBlur) {
            context.getSystemService(WindowManager::class.java).removeCrossWindowBlurEnabledListener(blurListener)
            observingBlur = false
        }
        super.onStop()
        onClosed?.invoke()
    }
}
