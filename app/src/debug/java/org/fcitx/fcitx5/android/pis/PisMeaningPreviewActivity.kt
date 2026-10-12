/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.keyboard.TextKeyboard

/** Debug-only preview using the same word dialog and keyboard views as the IME. */
class PisMeaningPreviewActivity : Activity() {
    private var dialog: PisCandidateMeaningDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PisAppearance.apply(this, if (intent.getBooleanExtra("dark", false)) PisAppearance.dark else PisAppearance.light)
        val theme = ThemeManager.activeTheme
        val status = TextView(this).apply {
            text = "词义弹窗样式预览"; textSize = 18f
            setTextColor(theme.keyTextColor)
            setPadding(dp(20), dp(32), dp(20), 0)
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(theme.backgroundColor)
            addView(status)
            addView(TextKeyboard(this@PisMeaningPreviewActivity, theme),
                FrameLayout.LayoutParams(-1, dp(240), Gravity.BOTTOM))
        }
        setContentView(root)
        root.post {
            val word = intent.getStringExtra("word") ?: "technology"
            dialog = PisCandidateMeaningDialog(this, theme, word) { status.text = "候选操作已调用" }.apply {
                onClosed = { status.text = "弹窗已关闭" }
                show()
                when (intent.getStringExtra("body")) {
                    "empty" -> setDefinition("")
                    "loading" -> Unit
                    "long" -> setDefinition("英文词条\n$word\n\n中文释义与音标\n" + "技术；工艺；应用科学\n\nA practical definition with enough detail to scroll.\n\n".repeat(18))
                    else -> setDefinition("英文词条\n$word\n\n中文释义与音标\n/tekˈnɒlədʒi/\n\nn. 技术；工艺；应用科学\n\n将科学知识应用于实际用途的方法与工具。")
                }
            }
        }
    }

    override fun onDestroy() { dialog?.dismiss(); super.onDestroy() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
