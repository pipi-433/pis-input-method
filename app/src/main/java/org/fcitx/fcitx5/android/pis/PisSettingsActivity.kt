/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.BuildConfig
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.fcitx.fcitx5.android.pis.voice.VoicePipeline
import org.fcitx.fcitx5.android.pis.voice.VoicePostProcessor

/** Small pis control panel: enable, colors and height, without the upstream navigation UI. */
class PisSettingsActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var enableButton: Button
    private lateinit var pickButton: Button
    private val colors = mutableListOf<EditText>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        scroll.clipToPadding = true
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(24)) }
        scroll.addView(root)
        scroll.setBackgroundColor(Color.parseColor("#F3F4F6"))
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        setContentView(scroll)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        ViewCompat.requestApplyInsets(scroll)
        label("pis'input method", 25)
        label("键盘外观", 16)
        status = label("", 14)
        enableButton = button("启用输入法") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        pickButton = button("选择输入法") { getSystemService(InputMethodManager::class.java).showInputMethodPicker() }
        if (BuildConfig.DEBUG) {
            button("打开输入验证") {
                startActivity(Intent().setClassName(this, "org.fcitx.fcitx5.android.pis.PisKeyboardTestActivity"))
            }
        }
        label("主题颜色", 20)
        val presets = LinearLayout(this)
        listOf("浅灰" to PisAppearance.light, "深色" to PisAppearance.dark, "雾蓝" to PisAppearance.blue).forEach { (name, palette) ->
            presets.addView(Button(this).apply { text = name; setOnClickListener { PisAppearance.apply(this@PisSettingsActivity, palette); loadColors(); message("主题已保存") } }, LinearLayout.LayoutParams(0, dp(52), 1f))
        }
        root.addView(presets)
        val pageRoot = root
        val colorPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        root = colorPanel
        listOf("键盘底色", "字母键颜色", "功能键颜色", "文字颜色").forEach { title ->
            label(title, 14)
            colors += EditText(this).apply {
                setSingleLine(true); hint = "#RRGGBB"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                root.addView(this)
            }
        }
        loadColors()
        button("保存自定义颜色") {
            try {
                val values = colors.map { it.text.toString().trim() }
                require(values.all { it.matches(Regex("#[0-9a-fA-F]{6}")) }) { "请输入 #RRGGBB 格式，如 #E9EAED" }
                PisAppearance.apply(this, PisAppearance.Palette(values[0], values[1], values[2], values[3]))
                message("颜色已保存，下次弹出键盘生效")
            } catch (error: IllegalArgumentException) { message(error.message ?: "颜色格式不正确") }
        }
        root = pageRoot
        button("自定义颜色") { colorPanel.visibility = if (colorPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        root.addView(colorPanel)
        label("键盘高度", 20)
        heightSlider("竖屏", AppPrefs.getInstance().keyboard.keyboardHeightPercent, 24, 45)
        heightSlider("横屏", AppPrefs.getInstance().keyboard.keyboardHeightPercentLandscape, 35, 65)
        label("高度按屏幕比例设置，收起后重新弹出键盘即可查看。", 13)
        label("底部上移", 20)
        preferenceSlider("竖屏", AppPrefs.getInstance().keyboard.keyboardBottomPadding, 0, 80, "dp")
        preferenceSlider("横屏", AppPrefs.getInstance().keyboard.keyboardBottomPaddingLandscape, 0, 48, "dp")
        label("上移只增加键盘下方留白，按键高度不变。", 13)
        label("按键反馈", 20)
        val keyboard = AppPrefs.getInstance().keyboard
        val enabledMode = org.fcitx.fcitx5.android.data.InputFeedbacks.InputFeedbackMode.Enabled
        val disabledMode = org.fcitx.fcitx5.android.data.InputFeedbacks.InputFeedbackMode.Disabled
        feedbackSwitch("按键音", keyboard.soundOnKeyPress.getValue() != disabledMode) { keyboard.soundOnKeyPress.setValue(if (it) enabledMode else disabledMode) }
        preferenceSlider("音量", keyboard.soundOnKeyPressVolume, 0, 100, "%（0 跟随系统）")
        feedbackSwitch("震动", keyboard.hapticOnKeyPress.getValue() != disabledMode) { keyboard.hapticOnKeyPress.setValue(if (it) enabledMode else disabledMode) }
        preferenceSlider("震动时长", keyboard.buttonPressVibrationMilliseconds, 0, 30, "ms（0 跟随系统）")
        preferenceSlider("震动强度", keyboard.buttonPressVibrationAmplitude, 0, 255, "（0 跟随系统）")
        label("震动强度需要设备支持，且震动时长不为 0；设备静音或系统限制可能影响反馈。", 13)
        label("输入选项", 20)
        inputOptions()
        button("个人词频管理") { startActivity(Intent(this,PisWordFrequencyActivity::class.java)) }
        feedbackSwitch("字母键面保持大写", keyboard.keepLettersUppercase.getValue()) { keyboard.keepLettersUppercase.setValue(it) }
        feedbackSwitch("按键弹出预览", keyboard.popupOnKeyPress.getValue()) { keyboard.popupOnKeyPress.setValue(it) }
        label("中/英键切换拼音与英文，123 键打开纯数字九宫格。", 13)
        voiceOptions()
        if (BuildConfig.DEBUG) {
            label("试输入", 20)
            root.addView(EditText(this).apply { hint = "测试拼音、英文和标点"; minLines = 2; gravity = Gravity.TOP })
            root.addView(EditText(this).apply { hint = "测试数字九宫格"; inputType = android.text.InputType.TYPE_CLASS_NUMBER })
        }
        label("版本：${BuildConfig.VERSION_NAME}", 12)
        button("版权与资源来源") { showNotices() }
        label("输入引擎与按键组件基于 Fcitx5 Android（LGPL-2.1-or-later），界面与布局由 pis 定制。", 12)
    }
    override fun onResume() {
        super.onResume()
        refreshInputMethodStatus()
        PisWarmup.schedule(this)
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::status.isInitialized) refreshInputMethodStatus()
    }
    private fun refreshInputMethodStatus() {
        val manager = getSystemService(InputMethodManager::class.java)
        val enabled = manager.enabledInputMethodList.any { it.packageName == packageName }
        val selected = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith("$packageName/") == true
        status.text = when { selected -> "正在使用 pis 输入法"; enabled -> "已启用，请选择为当前输入法"; else -> "先启用，再选择输入法" }
        enableButton.visibility = if (enabled) View.GONE else View.VISIBLE
        pickButton.visibility = if (selected) View.GONE else View.VISIBLE
    }
    private fun loadColors() {
        val p = PisAppearance.palette(this)
        listOf(p.background, p.keys, p.functions, p.text).forEachIndexed { index, value -> colors[index].setText(value) }
    }
    private fun voiceOptions() {
        label("长按空格说话，松手结束，上滑取消。长按句号翻译，长按逗号打开剪贴板。", 13)
        val pageRoot = root
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        root = panel
        val store = VoicePipeline.store(this)
        val saved = store.get()
        fun option(title: String, value: Boolean) = Switch(this).apply {
            text = title; isChecked = value; minHeight = dp(48); root.addView(this)
        }
        val punctuation = option("自动补充标点", saved.punctuation)
        val candidates = option("显示语音修正候选", saved.candidates)
        val common = option("通用热词", saved.common)
        val learn = option("记住个人热词的使用次数", saved.learn)
        fun field(hintText: String, value: String) = EditText(this).apply {
            hint = hintText; setText(value); minLines = 3; maxLines = 6; gravity = Gravity.TOP
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            root.addView(this)
        }
        label("个人热词（每行一个，最多 256 个）", 14)
        val words = field("例如：项目名称、人名", saved.words.joinToString("\n") { "${it.word}\t${it.boost}" })
        label("自定义修正（每行：错词=>正确词）", 14)
        val aliases = field("例如：错写的人名=>正确的人名", saved.aliases.joinToString("\n") { "${it.heard}=>${it.word}" })
        label("原文候选始终保留；明确的电脑操作语境可优先修正同音词，其他修正由你选择。只记个人热词的次数，不保存录音或完整句子。", 13)
        button("保存语音选项") {
            try {
                val edited = VoicePostProcessor.edited(words.text.toString(), aliases.text.toString(), store.get(), punctuation.isChecked, candidates.isChecked, learn.isChecked)
                store.save(VoicePostProcessor.withCommon(edited, common.isChecked))
                message("语音选项已保存")
            } catch (error: Exception) { message(error.message ?: "无法保存语音选项") }
        }
        button("清零热词使用次数") { runCatching { store.resetCounts() }.onSuccess { message("使用次数已清零") }.onFailure { message("未能清零，请重试") } }
        root = pageRoot
        button("语音输入选项") { panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        root.addView(panel)
    }
    private fun inputOptions() {
        val modes = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val full = RadioButton(this).apply { id = View.generateViewId(); text = "全拼" }
        val half = RadioButton(this).apply { id = View.generateViewId(); text = "双拼（首道）" }
        modes.addView(full); modes.addView(half); root.addView(modes)
        modes.check(if (PisInputOptions.scheme(this) == "shoudao") half.id else full.id)
        label("全拼：nihao；首道双拼：nihd。双拼状态下，小标提示对应声母和韵母。",13)
        label("首道零声母：UE → e，UI → ei，UF → eng。字母键长按或上滑输入角标数字/符号。",13)
        label("个人词频由输入引擎学习，全拼与首道双拼共用新词库。长按候选可使用引擎提供的删词操作。",13)
        val controls = linkedMapOf<String, Switch>()
        listOf("prediction" to "中文联想", "learning" to "打字学习新词",
            "smart_enter" to "智能回车（跟随发送/搜索等动作）", "fuzzy_flat" to "模糊音：平翘舌", "fuzzy_nasal" to "模糊音：前后鼻音",
            "fuzzy_southern" to "模糊音：南方音").forEach { (key,title) ->
            controls[key] = Switch(this).apply {
                text = title; minHeight = dp(48)
                isChecked = PisInputOptions.bool(this@PisSettingsActivity,key,key in listOf("prediction","learning","smart_enter"))
                root.addView(this)
            }
        }
        button("保存输入选项") {
            PisInputOptions.save(this,if (modes.checkedRadioButtonId == half.id) "shoudao" else "full",controls.mapValues { it.value.isChecked })
            message("已保存，重新弹出键盘生效")
        }
    }
    private fun heightSlider(title: String, preference: org.fcitx.fcitx5.android.data.prefs.ManagedPreference.PInt, min: Int, max: Int) {
        preferenceSlider(title, preference, min, max, "%")
    }
    private fun preferenceSlider(title: String, preference: org.fcitx.fcitx5.android.data.prefs.ManagedPreference.PInt, min: Int, max: Int, unit: String) {
        val value = preference.getValue().coerceIn(min, max)
        val text = label("$title：$value$unit", 15)
        root.addView(SeekBar(this).apply {
            this.max = max - min; progress = value - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(view: SeekBar?, progress: Int, fromUser: Boolean) { text.text = "$title：${progress + min}$unit" }
                override fun onStartTrackingTouch(view: SeekBar?) {}
                override fun onStopTrackingTouch(view: SeekBar?) { preference.setValue((view?.progress ?: 0) + min) }
            })
        })
    }
    private fun feedbackSwitch(title: String, enabled: Boolean, update: (Boolean) -> Unit) {
        root.addView(Switch(this).apply { text = title; isChecked = enabled; minHeight = dp(48); setOnCheckedChangeListener { _, checked -> update(checked) } })
    }
    private fun label(text: String, size: Int) = TextView(this).apply { this.text = text; textSize = size.toFloat(); setTextColor(Color.parseColor("#303642")); setPadding(0, dp(10), 0, dp(5)); root.addView(this) }
    private fun button(text: String, action: () -> Unit): Button = Button(this).apply { this.text = text; setOnClickListener { action() }; root.addView(this) }
    private fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    private fun showNotices() {
        val text = assets.open("pis-licenses/SOURCES.md").bufferedReader().use { it.readText() }
        val content = TextView(this).apply {
            this.text = text; textSize = 13f; setTextColor(Color.parseColor("#303642"))
            setPadding(dp(16), dp(12), dp(16), dp(12)); setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply { addView(content) }
        android.app.AlertDialog.Builder(this).setTitle("版权与资源来源")
            .setView(scroll).setPositiveButton("关闭", null).show()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
