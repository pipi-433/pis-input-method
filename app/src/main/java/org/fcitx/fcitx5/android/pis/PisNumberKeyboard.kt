/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.keyboard.*
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.popup.PopupAction
import splitties.views.imageResource
import kotlin.math.roundToInt

/** Numeric reference layout: scrolling symbols, 1-9 grid, wide zero and a two-row return. */
@SuppressLint("ViewConstructor")
open class PisNumberKeyboard(context:Context,theme:Theme):BaseKeyboard(context,theme,emptyList()) {
    private data class Cell(val view:KeyView,val column:Int,val row:Int,val span:Int=1,val bottom:Int=-1)
    private val cells=mutableListOf<Cell>()
    private val symbols=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
    private val strip=ScrollView(context).apply {
        isVerticalScrollBarEnabled=true;isFillViewport=false
        descendantFocusability=android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
        background=GradientDrawable().apply { setColor(theme.keyBackgroundColor);cornerRadius=dp(4).toFloat() }
        addView(symbols)
    }
    private val returnKey:ImageKeyView
    private fun dp(value:Int)=(value*resources.displayMetrics.density).roundToInt()
    private fun textKey(text:String,size:Float=22f,alternative:Boolean=false,viewId:Int=-1,flat:Boolean=false)=KeyDef(
        KeyDef.Appearance.Text(text,size,variant=if(alternative) KeyDef.Appearance.Variant.Alternative else KeyDef.Appearance.Variant.Normal,
            viewId=viewId,margin=!flat,border=if(flat) KeyDef.Appearance.Border.Off else KeyDef.Appearance.Border.Default),
        setOf(KeyDef.Behavior.Press(KeyAction.CommitAction(text))))
    private fun cell(def:KeyDef,column:Int,row:Int,span:Int=1,bottom:Int=-1)=createSpannedKey(def).also { cells+=Cell(it,column,row,span,bottom);addView(it) }
    init {
        for(row in 0..2) for(column in 0..2) cell(textKey((row*3+column+1).toString()),column,row)
        cell(BackspaceKey(),3,0);cell(textKey(".",22f,true),3,1)
        returnKey=cell(ReturnKey(),3,2,span=2) as ImageKeyView
        cell(TextPickerSwitchKey("符",PickerWindow.Key.Symbol,variant=KeyDef.Appearance.Variant.Alternative),-1,3)
        cell(LayoutSwitchKey("返回",TextKeyboard.Name,variant=KeyDef.Appearance.Variant.Alternative),0,3,bottom=0)
        cell(textKey("0"),1,3,bottom=1)
        val space=textKey("⎵",28f,true,R.id.button_mini_space)
        cell(KeyDef(space.appearance,setOf(KeyDef.Behavior.Press(KeyAction.CommitAction(" ")))),2,3,bottom=2)
        listOf("@","%","/","-","(",")","+","=","*","#",":",",").forEach { symbol ->
            symbols.addView(createSpannedKey(textKey(symbol,20f,flat=true)),LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,dp(40)))
        }
        addView(strip)
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val width=View.MeasureSpec.getSize(widthMeasureSpec);val height=View.MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width,height)
        val side=(width*0.17f).roundToInt();val middle=width-2*side;val rowHeight=height/4
        cells.forEach { item ->
            val w=when {item.column==-1 || item.column==3 ->side;item.bottom==1 ->middle/2;item.bottom>=0 ->middle/4;else ->middle/3}
            item.view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(rowHeight*item.span,View.MeasureSpec.EXACTLY))
        }
        val stripHeight=rowHeight*3-dp(10)
        for(i in 0 until symbols.childCount) symbols.getChildAt(i).layoutParams.height=maxOf(dp(30),stripHeight/4)
        strip.measure(View.MeasureSpec.makeMeasureSpec(side-dp(6),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(stripHeight,View.MeasureSpec.EXACTLY))
    }
    override fun onLayout(changed:Boolean,left:Int,top:Int,right:Int,bottom:Int) {
        val w=right-left;val h=bottom-top;val side=(w*0.17f).roundToInt();val middle=w-side*2;val rowHeight=h/4
        cells.forEach { item ->
            val x=when {item.column==-1 ->0;item.column==3 ->w-side;item.bottom==0 ->side;item.bottom==1 ->side+middle/4;item.bottom==2 ->side+middle*3/4;else ->side+middle*item.column/3}
            val y=rowHeight*item.row
            item.view.layout(x,y,x+item.view.measuredWidth,y+item.view.measuredHeight)
        }
        strip.layout(dp(3),dp(5),side-dp(3),rowHeight*3-dp(5))
    }
    override fun onReturnDrawableUpdate(returnDrawable:Int) { returnKey.img.imageResource=returnDrawable }
    override fun onAttach() { strip.post { strip.scrollTo(0,0) } }
    @SuppressLint("MissingSuperCall") override fun onPopupAction(action:PopupAction) {}
}
