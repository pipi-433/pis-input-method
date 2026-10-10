/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/** Edge-only glass sheen, drawn above bar content without tinting or blurring the text. */
class PisGlassBarDrawable(private val radius: Float, private val density: Float, private val dark: Boolean) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = RectF()
    private val glint = RectF()
    private var rimShader: Shader? = null
    private var glintShader: Shader? = null
    private var opacity = 255

    override fun onBoundsChange(bounds: Rect) {
        val d = density
        rim.set(bounds)
        rim.inset(.5f * d, .5f * d)
        val colors = if (dark) intArrayOf(0x3cffffff, 0x1effffff, 0x149ab4cd, 0x32ffffff, 0x1e7b91a7)
            else intArrayOf(0x98ffffff.toInt(), 0x44ffffff, 0x1eafc2d6, 0x64ffffff, 0x2caab8c4)
        rimShader = LinearGradient(rim.left, rim.top, rim.right, rim.bottom, colors,
            floatArrayOf(0f, .28f, .54f, .78f, 1f), Shader.TileMode.CLAMP)
        glint.set(bounds.left + 10f * d, bounds.top + 1.3f * d,
            bounds.left + bounds.width() * .66f, bounds.top + 1.7f * d)
        glintShader = LinearGradient(glint.left, 0f, glint.right, 0f,
            intArrayOf(0x00ffffff, if (dark) 0x2cffffff else 0x5affffff, 0x00ffffff),
            floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        if (rim.isEmpty) return
        val checkpoint = if (opacity < 255) canvas.saveLayerAlpha(bounds.left.toFloat(), bounds.top.toFloat(),
            bounds.right.toFloat(), bounds.bottom.toFloat(), opacity) else canvas.save()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = .9f * density
        paint.color = android.graphics.Color.WHITE
        paint.shader = rimShader
        canvas.drawRoundRect(rim, radius - .5f * density, radius - .5f * density, paint)
        paint.shader = null
        // A faint lower rim adds depth while the center stays one solid theme color.
        canvas.save()
        canvas.clipRect(rim.left, rim.centerY(), rim.right, rim.bottom + density)
        paint.strokeWidth = .4f * density
        paint.color = if (dark) 0x28697c90 else 0x19697c90
        canvas.drawRoundRect(rim, radius - .5f * density, radius - .5f * density, paint)
        canvas.restore()
        paint.style = Paint.Style.FILL
        paint.color = android.graphics.Color.WHITE
        paint.shader = glintShader
        canvas.drawRect(glint, paint)
        paint.shader = null
        canvas.restoreToCount(checkpoint)
    }

    override fun getPadding(padding: Rect): Boolean { padding.set(0, 0, 0, 0); return false }
    override fun setAlpha(alpha: Int) { opacity = alpha.coerceIn(0, 255); invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
