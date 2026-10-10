/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils

/** Raised key surface drawn within the original key margins; no new touch or layout layer. */
class PisKeycapDrawable(
    private val faceColor: Int,
    private val shadowColor: Int,
    private val radius: Float,
    private val horizontalMargin: Int,
    private val verticalMargin: Int,
    private val density: Float,
    private val contentPadding: Rect
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val face = RectF()
    private val edge = RectF()
    private val ambient = RectF()
    private val shadow = RectF()
    private var pressed = false
    private var opacity = 255
    private var faceShader: Shader? = null
    private var rimShader: Shader? = null
    private val dark = ColorUtils.calculateLuminance(faceColor) < .35
    private val whiteKey = ColorUtils.calculateLuminance(faceColor) > .8

    override fun onBoundsChange(bounds: Rect) { updateSurface() }

    private fun updateSurface() {
        val d = density
        val down = if (pressed) .75f * d else 0f
        face.set(bounds.left + horizontalMargin.toFloat(), bounds.top + verticalMargin + down,
            bounds.right - horizontalMargin.toFloat(), bounds.bottom - verticalMargin - 1.4f * d + down)
        if (face.isEmpty) return
        val top = if (!pressed && !dark && whiteKey) Color.WHITE else ColorUtils.blendARGB(faceColor, Color.WHITE,
            if (pressed) .06f else if (dark) .10f else .30f)
        val middle = if (!pressed && !dark) faceColor else ColorUtils.blendARGB(faceColor,
            if (pressed) Color.BLACK else Color.WHITE, if (pressed) .035f else .015f)
        val bottom = when {
            pressed -> ColorUtils.blendARGB(faceColor, Color.BLACK, .055f)
            dark -> ColorUtils.blendARGB(faceColor, Color.BLACK, .035f)
            whiteKey -> ColorUtils.blendARGB(faceColor, 0xffa9b2c1.toInt(), .07f)
            else -> faceColor
        }
        faceShader = LinearGradient(0f, face.top, 0f, face.bottom,
            intArrayOf(top, middle, bottom), floatArrayOf(0f, .36f, 1f), Shader.TileMode.CLAMP)
        rimShader = LinearGradient(0f, face.top, 0f, face.bottom,
            intArrayOf(if (dark) 0x38ffffff else 0xe8ffffff.toInt(), if (dark) 0x08ffffff else 0x28ffffff),
            null, Shader.TileMode.CLAMP)
        invalidateSelf()
    }

    override fun isStateful() = true
    override fun getPadding(padding: Rect): Boolean {
        padding.set(contentPadding)
        return padding.left != 0 || padding.top != 0 || padding.right != 0 || padding.bottom != 0
    }

    override fun onStateChange(state: IntArray): Boolean {
        val next = state.contains(android.R.attr.state_pressed)
        if (next == pressed) return false
        pressed = next
        updateSurface()
        return true
    }

    override fun draw(canvas: Canvas) {
        if (face.isEmpty) return
        val d = density
        val checkpoint = if (opacity < 255) canvas.saveLayerAlpha(bounds.left.toFloat(), bounds.top.toFloat(),
            bounds.right.toFloat(), bounds.bottom.toFloat(), opacity) else canvas.save()
        paint.style = Paint.Style.FILL
        paint.shader = null
        // Several thin, translucent silhouettes give a soft shadow without a software blur layer.
        for (step in 3 downTo 1) {
            ambient.set(face)
            ambient.inset(-step * .3f * d, -step * .18f * d)
            ambient.offset(0f, (if (pressed) .55f else 1.25f) * d)
            paint.color = ColorUtils.setAlphaComponent(shadowColor, if (pressed) 9 else 11 + (3 - step) * 9)
            canvas.drawRoundRect(ambient, radius + step * .25f * d, radius + step * .25f * d, paint)
        }
        shadow.set(face)
        shadow.offset(0f, (if (pressed) .45f else 1.3f) * d)
        paint.color = ColorUtils.blendARGB(shadowColor, faceColor, .25f)
        canvas.drawRoundRect(shadow, radius, radius, paint)

        paint.shader = faceShader
        canvas.drawRoundRect(face, radius, radius, paint)
        paint.shader = null

        edge.set(face)
        edge.inset(.35f * d, .35f * d)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = .7f * d
        paint.shader = rimShader
        canvas.drawRoundRect(edge, (radius - .35f * d).coerceAtLeast(0f),
            (radius - .35f * d).coerceAtLeast(0f), paint)
        paint.shader = null
        paint.style = Paint.Style.FILL
        canvas.restoreToCount(checkpoint)
    }

    override fun setAlpha(alpha: Int) { opacity = alpha.coerceIn(0, 255); invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
