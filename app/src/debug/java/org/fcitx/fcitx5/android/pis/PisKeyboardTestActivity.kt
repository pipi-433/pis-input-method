/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** A real editable field for user-operated emulator testing, with direct settings/word-list access. */
class PisKeyboardTestActivity : Activity() {
    private lateinit var editor: EditText
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(12)); setBackgroundColor(Color.rgb(248,249,251)) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view,insets -> val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()); view.setPadding(dp(16)+bars.left,dp(12)+bars.top,dp(16)+bars.right,dp(12)+bars.bottom); insets }
        root.addView(TextView(this).apply { text="pis 输入验证"; textSize=22f; setTextColor(Color.rgb(48,54,66)) })
        val toolbar=LinearLayout(this)
        listOf("外观与输入设置" to PisSettingsActivity::class.java,"个人词频管理" to PisWordFrequencyActivity::class.java).forEach { (title,target) ->
            toolbar.addView(Button(this).apply { text=title; textSize=13f; setOnClickListener { startActivity(Intent(this@PisKeyboardTestActivity,target)) } },LinearLayout.LayoutParams(0,dp(48),1f))
        }
        root.addView(toolbar)
        root.addView(TextView(this).apply { text="全拼 nihao / 首道双拼 nihd；长按候选看释义。\n逗号：剪贴板 · 空格：语音 · 句号：整句英译"; textSize=13f; setPadding(0,dp(8),0,dp(8)) })
        editor=EditText(this).apply {
            hint="点击这里，直接试输入"; textSize=18f; minLines=3; maxLines=5; gravity=Gravity.TOP
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions=EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        }
        root.addView(editor)
        root.addView(Button(this).apply { text="清空试输入内容"; setOnClickListener { editor.text.clear(); showKeyboard() } })
        setContentView(ScrollView(this).apply { isFillViewport=true; addView(root) })
    }
    override fun onResume() { super.onResume(); if(::editor.isInitialized) editor.postDelayed({ showKeyboard() },350) }
    private fun showKeyboard() { editor.requestFocus(); getSystemService(InputMethodManager::class.java).showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT) }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
}
