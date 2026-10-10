/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.text.TextWatcher
import android.text.Editable
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.daemon.FcitxConnection

class PisWordFrequencyActivity : Activity() {
    private val scope = MainScope()
    private lateinit var engine: FcitxConnection
    private val client = "pis-word-frequency-${System.identityHashCode(this)}"
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var search: EditText
    private var rows = emptyList<PisWordFrequency.Row>()
    private var limit = 80
    private var busy = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = FcitxDaemon.connect(client)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20),dp(12),dp(20),dp(24)); setBackgroundColor(Color.rgb(243,244,246)) }
        scroll.addView(root)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view,insets -> val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()); view.setPadding(bars.left,bars.top,bars.right,bars.bottom); insets }
        setContentView(scroll)
        fun label(value: String,size: Float = 14f) = TextView(this).apply { text=value; textSize=size; setTextColor(Color.rgb(48,54,66)); setPadding(0,dp(8),0,dp(8)); root.addView(this) }
        label("个人词频管理",24f)
        label("全拼和首道双拼共用。次数是引擎的近期学习记录，优先级 1 置顶、0 取消固定排序。修改会影响实际候选。")
        status = label("正在读取…")
        search = field("搜索词语或拼音",false).also { root.addView(it) }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) {}
            override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) { limit = 80; render() }
            override fun afterTextChanged(s: Editable?) {}
        })
        root.addView(Button(this).apply { text="添加词条"; setOnClickListener { edit(null) } })
        root.addView(Button(this).apply { text="刷新词频"; setOnClickListener { load() } })
        root.addView(Button(this).apply { text="清空中文学习与固定排序"; setOnClickListener { confirm("清空所有中文学习？","只清空中文个人词库、近期统计及固定排序；语音热词和释义库保留。") { change("clear",null) } } })
        list = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; root.addView(list)
        load()
    }
    private fun field(hintText: String,number: Boolean): EditText = EditText(this).apply {
        hint=hintText; setSingleLine(true)
        inputType = if (number) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
    }
    private fun load() {
        if (busy) return
        busy = true; status.text="正在读取…"
        scope.launch {
            runCatching { engine.runOnReady { PisWordFrequency.rows(this) } }
                .onSuccess { rows=it; render() }.onFailure { status.text="读取失败：${it.message}" }
            busy=false
        }
    }
    private fun render() {
        if (!::list.isInitialized) return
        val keyword = search.text.toString().trim().lowercase()
        val filtered = rows.filter { keyword.isEmpty() || it.word.contains(keyword) || it.reading.replace("'","").contains(keyword.replace(" ","").replace("'","")) }
        status.text="共 ${rows.size} 个词条，匹配 ${filtered.size} 个（最多显示 10000 条）"
        list.removeAllViews()
        filtered.take(limit).forEach { row ->
            list.addView(Button(this).apply {
                text="${row.word}   ${row.reading.replace("'"," ")}\n次数 ${row.count}   优先级 ${row.priority}"
                isAllCaps=false; gravity=Gravity.START or Gravity.CENTER_VERTICAL
                setOnClickListener { edit(row) }
            })
        }
        if (filtered.isEmpty()) list.addView(TextView(this).apply { text=if (rows.isEmpty()) "还没有学习记录。正常选词后刷新，或添加新词。" else "没有匹配的词条"; setPadding(0,dp(20),0,dp(20)) })
        if (filtered.size > limit) list.addView(Button(this).apply { text="显示更多"; setOnClickListener { limit += 80; render() } })
    }
    private fun edit(row: PisWordFrequency.Row?) {
        if (busy) return
        val panel = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),0,dp(20),0) }
        fun entry(hint: String,number: Boolean,value: String): EditText {
            panel.addView(TextView(this).apply { text=hint; textSize=13f; setPadding(0,dp(6),0,0) })
            return field(hint,number).also { it.setText(value); panel.addView(it) }
        }
        val word = entry("词语",false,row?.word.orEmpty()).apply { isEnabled=row==null }
        val reading = entry("全拼音节，用空格分开：ni hao",false,row?.reading?.replace("'"," ").orEmpty()).apply { isEnabled=row==null }
        val count = entry("近期次数：可调整到0–1000",true,(row?.count ?: 1).toString())
        val priority = entry("优先级：0不固定，1置顶，2–100指定位置",true,(row?.priority ?: 0).toString())
        panel.addView(TextView(this).apply { text="调整次数会重新建立该词的统计及相关联想；不会改语音词库。"; setPadding(0,dp(8),0,dp(8)) })
        val dialog = AlertDialog.Builder(this).setTitle(row?.word ?: "添加个人词条").setView(ScrollView(this).apply { addView(panel) }).setNegativeButton("取消",null).setPositiveButton("保存",null)
            .setNeutralButton(if (row == null) "" else "更多操作",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val n=count.text.toString().toInt(); val p=priority.text.toString().toInt()
                    require((n in 0..1000 || n==row?.count) && p in 0..100) { "次数0–1000，优先级0–100" }
                    val r=row ?: PisWordFrequency.Row(word.text.toString().trim(),PisWordFrequency.normalizeReading(reading.text.toString()),0,0f,0)
                    change("set",r,n,p); dialog.dismiss()
                } catch (error: Exception) { Toast.makeText(this,error.message,Toast.LENGTH_LONG).show() }
            }
            if (row != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                AlertDialog.Builder(this).setTitle(row.word).setItems(arrayOf("次数清零（保留词条）","删除学习记录和固定排序")) { _,index ->
                    dialog.dismiss(); confirm(if(index==0) "清零这个词？" else "删除这个词？","将移除该词相关的学习统计和联想；基本词典不受影响。") { change(if(index==0) "reset" else "delete",row) }
                }.show()
            }
        }
        dialog.show()
    }
    private fun confirm(title: String,message: String,action: ()->Unit) = AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("取消",null).setPositiveButton("确定") { _,_ -> action() }.show()
    private fun change(action: String,row: PisWordFrequency.Row?,count: Int=0,priority: Int=0) {
        if (busy) return
        busy=true; status.text="正在保存…"
        scope.launch {
            runCatching { engine.runOnReady { PisWordFrequency.change(this,this@PisWordFrequencyActivity,action,row,count,priority); PisWordFrequency.rows(this) } }
                .onSuccess { rows=it; render(); Toast.makeText(this@PisWordFrequencyActivity,"已保存",Toast.LENGTH_SHORT).show() }
                .onFailure { status.text="未保存：${it.message}" }
            busy=false
        }
    }
    override fun onDestroy() { scope.cancel(); FcitxDaemon.disconnect(client); super.onDestroy() }
    private fun dp(value: Int) = (value*resources.displayMetrics.density).toInt()
}
