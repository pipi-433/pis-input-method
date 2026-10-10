/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** Fixed 中/英 order, with a strong size contrast for the currently active language. */
class PisLanguageLabel(context:Context,private val ink:Int):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var chinese=true
    init { isClickable=false;isFocusable=false }
    fun setChinese(value:Boolean) { chinese=value;invalidate() }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val density=resources.displayMetrics.density
        val sizes=floatArrayOf(if(chinese) 20f else 9f,11f,if(chinese) 9f else 20f)
        val texts=arrayOf("中","/","英")
        val widths=FloatArray(3) { paint.textSize=sizes[it]*density;paint.measureText(texts[it]) }
        val gap=2f*density
        val total=widths.sum()+gap*2
        val scale=minOf(1f,((width-6*density)/total).coerceAtLeast(0.1f))
        canvas.save();canvas.translate(width/2f,height/2f);canvas.scale(scale,scale)
        var x=-total/2
        for(i in 0..2) {
            paint.color=ink;paint.textSize=sizes[i]*density
            paint.alpha=if(i==1) 150 else if((i==0)==chinese) 255 else 165
            val metrics=paint.fontMetrics
            canvas.drawText(texts[i],x,-(metrics.ascent+metrics.descent)/2,paint)
            x+=widths[i]+gap
        }
        canvas.restore()
    }
}
