/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis.feedback

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.pis.feedback.PisFeedbackEvent.Kind
import org.fcitx.fcitx5.android.pis.feedback.PisFeedbackEvent.Phase
import org.fcitx.fcitx5.android.pis.feedback.PisFeedbackEvent.TaskKind
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Fixed 2 x 12 LEDs. One animator, no frame queue, no input/recognition ownership. */
class PisFeedbackView(context: Context, private val theme: Theme) : View(context) {
    companion object { const val WIDTH_DP = 68 }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panel = RectF()
    private val xs = FloatArray(24)
    private val ys = FloatArray(24)
    private val levels = FloatArray(24)
    private val startLevels = FloatArray(24)
    private val colors = IntArray(24)
    private val startColors = IntArray(24)
    private val tail = IntArray(3) { -1 }
    private val lampColors = intArrayOf(0xffff5f57.toInt(), 0xfffebc2e.toInt(), 0xff28c840.toInt())
    private val glowShaders = arrayOfNulls<Shader>(24)
    private val lampVisualGain = 3f
    private var pulse = PisFeedbackEvent.Pulse(Kind.Key)
    private var task: PisFeedbackEvent.Task? = null
    private var chinese = true
    private var idle = true
    private var progress = 1f
    private var voiceLevel = 0f
    private var lastKeyAt = 0L
    private var deleteStart = 11f
    private var deleteEnd = 0f
    private var playing = false
    private var looping = false
    private var returningToIdle = false
    private var repeated = false
    private val warm = 0xfffff7ec.toInt()
    // The reference uses one warm-white LED color; the three lamps convey status color.
    private val ice = warm
    private val red = warm
    private val green = warm
    private val blue = warm
    private val purple = warm
    private val yellow = warm
    private val dark = ColorUtils.calculateLuminance(theme.barColor) < .35
    private val applePalette = theme.barColor == 0xffe9ebef.toInt()
    private val panelColor = if (applePalette) 0xffd1d4da.toInt() else ColorUtils.blendARGB(theme.barColor, if (dark) Color.WHITE else Color.BLACK, .085f)
    private val unlit = if (applePalette) 0xffb0b6bf.toInt() else ColorUtils.blendARGB(panelColor, theme.candidateTextColor, if (dark) .28f else .13f)
    private val border = if (applePalette) 0xffc5c9d1.toInt() else ColorUtils.blendARGB(panelColor, theme.candidateTextColor, .10f)
    private val settle = Runnable {
        task = null
        beginIdleFade()
    }
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        interpolator = LinearInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            render(progress)
            invalidate()
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationRepeat(animation: Animator) { repeated = true }
            override fun onAnimationEnd(animation: Animator) {
                if (!playing || looping) return
                playing = false
                if (returningToIdle) {
                    returningToIdle = false
                    idle = true
                    drawIdle()
                } else {
                    render(1f)
                    postDelayed(settle, 2200L)
                }
                invalidate()
            }
        })
    }

    init {
        id = generateViewId()
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setBackgroundColor(theme.barColor)
        drawIdle()
    }

    private fun motionEnabled() = ValueAnimator.areAnimatorsEnabled() &&
        !AppPrefs.getInstance().advanced.disableAnimation.getValue()

    fun accept(event: PisFeedbackEvent) {
        if (event is PisFeedbackEvent.Reset) { reset(); return }
        if (event is PisFeedbackEvent.Language) chinese = event.chinese
        // Late asynchronous callbacks cannot restart a hidden/detached keyboard.
        if (!isAttachedToWindow || !isShown) return
        when (event) {
            is PisFeedbackEvent.Language -> {
                chinese = event.chinese
                if (event.animate) accept(PisFeedbackEvent.Pulse(Kind.Language))
            }
            is PisFeedbackEvent.Pulse -> {
                if (busy()) return
                val now = SystemClock.uptimeMillis()
                if (event.kind == Kind.Delete) {
                    if (pulse.kind == Kind.Delete && task == null && now - lastKeyAt < 450L && !idle) {
                        val eased = progress * progress * (3f - 2f * progress)
                        deleteStart += (deleteEnd - deleteStart) * eased
                        // Repeated key events approach column zero asymptotically; wrap after
                        // reaching its visible half-column instead of staying pinned there.
                        if (deleteStart <= .5f) { deleteStart = 11f; deleteEnd = 0f }
                        else deleteEnd = (deleteStart - 3f).coerceAtLeast(0f)
                    } else { deleteStart = 11f; deleteEnd = 0f }
                }
                if (event.kind == Kind.Key && pulse.kind == Kind.Key && now - lastKeyAt < 450L && !idle) {
                    tail[2] = tail[1]; tail[1] = tail[0]
                    tail[0] = pulse.row.coerceIn(0, 1) * 12 + pulse.column.coerceIn(0, 11)
                } else tail.fill(-1)
                lastKeyAt = now
                snapshot()
                task = null; pulse = event; idle = false
                start(when (event.kind) {
                    Kind.Enter -> 280L
                    Kind.Space, Kind.Language -> 240L
                    else -> 180L
                }, false)
            }
            is PisFeedbackEvent.Task -> {
                val previous = task
                if (event.phase == Phase.Canceled) {
                    if (previous?.kind == event.kind && previous.token == event.token) reset()
                    return
                }
                // Only a new preparation owns a task; stale completion cannot replace another task.
                if (event.phase != Phase.Preparing &&
                    (previous?.kind != event.kind || previous.token != event.token)) return
                snapshot(); task = event; idle = false; tail.fill(-1)
                val loop = busy()
                start(if (loop) 1100L else 260L, loop)
            }
            is PisFeedbackEvent.VoiceLevel -> {
                if (task?.kind == TaskKind.Voice && task?.token == event.token && task?.phase == Phase.Listening) {
                    voiceLevel = voiceLevel * .6f + event.level.coerceIn(0f, 1f) * .4f
                    render(progress); invalidate()
                }
            }
            PisFeedbackEvent.Reset -> Unit
        }
    }

    private fun busy() = when (task?.phase) {
        Phase.Preparing, Phase.Listening, Phase.Processing -> true
        else -> false
    }

    private fun snapshot() {
        levels.copyInto(startLevels); colors.copyInto(startColors)
        stopClock(); removeCallbacks(settle); returningToIdle = false
    }

    private fun start(durationMs: Long, loop: Boolean) {
        looping = loop
        repeated = false
        progress = 0f
        if (!motionEnabled()) {
            looping = false; render(1f); invalidate()
            if (!loop) postDelayed(settle, 2200L)
            return
        }
        animator.duration = durationMs
        animator.repeatCount = if (loop) ValueAnimator.INFINITE else 0
        playing = true
        animator.start()
    }

    private fun stopClock() { playing = false; looping = false; animator.cancel() }

    fun reset() {
        stopClock(); removeCallbacks(settle)
        task = null; tail.fill(-1); idle = true; returningToIdle = false; voiceLevel = 0f
        drawIdle(); invalidate()
    }

    private fun beginIdleFade() {
        snapshot(); returningToIdle = true
        if (!motionEnabled()) { returningToIdle = false; idle = true; drawIdle(); invalidate(); return }
        start(160L, false)
    }

    private fun drawIdle() {
        levels.fill(0f); colors.fill(warm)
        for (row in 0..1) for (col in 5..6) levels[row * 12 + col] = .25f
    }

    private fun dot(row: Int, col: Int, value: Float, color: Int) {
        if (row !in 0..1 || col !in 0..11) return
        val i = row * 12 + col
        if (value > levels[i]) { levels[i] = value.coerceIn(0f, 1f); colors[i] = color }
    }

    private fun pair(col: Int, value: Float, color: Int) {
        dot(0, col, value, color); dot(1, col, value, color)
    }

    private fun render(t: Float) {
        levels.fill(0f); colors.fill(warm)
        val ease = t * t * (3f - 2f * t)
        if (returningToIdle) {
            drawIdle()
            blendStart(ease)
            return
        }
        val activeTask = task
        if (activeTask != null) {
            when (activeTask.phase) {
                Phase.Preparing, Phase.Processing -> {
                    val col = (sin(t * Math.PI * 2) * 4.5 + 5.5).toFloat()
                    for (i in 0..11) pair(i, .16f + .65f * max(0f, 1f - abs(i - col) / 2f),
                        if (activeTask.kind == TaskKind.Voice) blue else purple)
                }
                Phase.Listening -> {
                    val spread = 1f + voiceLevel * 4.5f
                    for (i in 0..11) pair(i, .18f + (.25f + voiceLevel * .55f) * max(0f, 1f - abs(i - 5.5f) / spread), blue)
                }
                Phase.Ready, Phase.Success -> {
                    val distance = ease * 5.5f
                    for (i in 0..11) pair(i, .2f + (1f - t) * .65f * max(0f, 1f - abs(abs(i - 5.5f) - distance)), green)
                }
                Phase.Error -> {
                    for (i in 4..7) pair(i, .3f + (1f - t) * .6f, red)
                }
                Phase.Canceled -> drawIdle()
            }
        } else when (pulse.kind) {
            Kind.Key -> {
                val row = pulse.row.coerceIn(0, 1)
                val col = pulse.column.coerceIn(0, 11)
                val previous = tail[0].takeIf { it >= 0 }?.rem(12) ?: max(0, col - 1)
                val moving = previous + (col - previous) * ease
                for (i in 0..11) dot(row, i, (1f - t) * .85f * max(0f, 1f - abs(i - moving)), ice)
                dot(row, col, .32f + sin(t * Math.PI).toFloat() * .68f, ice)
                for (j in tail.indices) if (tail[j] >= 0) dot(tail[j] / 12, tail[j] % 12, (.3f - j * .07f) * (1f - t), ice)
            }
            Kind.Delete -> {
                val moving = deleteStart + (deleteEnd - deleteStart) * ease
                for (i in 0..11) pair(i, .8f * (1f - t) * max(0f, 1f - abs(i - moving) / 2f), red)
                // Keep the resting marker only after the sweep, not permanently at its left end.
                if (t >= 1f) pair(deleteEnd.roundToInt(), .28f, red)
            }
            Kind.Space -> {
                val distance = ease * 4.5f
                for (i in 0..11) pair(i, (1f - t) * max(0f, 1f - abs(abs(i - 5.5f) - distance)), green)
                pair(2, .24f, green); pair(9, .24f, green)
            }
            Kind.Confirm -> {
                dot(0, 5, .3f + sin(t * Math.PI).toFloat() * .65f, green)
                dot(0, 4, sin(t * Math.PI).toFloat() * .6f, green)
                dot(0, 6, sin(t * Math.PI).toFloat() * .6f, green)
            }
            Kind.Enter -> {
                val distance = 5.5f * (1f - (ease / .7f).coerceAtMost(1f))
                for (i in 0..11) pair(i, if (t in .68f.. .85f) .9f else
                    (1f - t) * max(0f, 1f - abs(abs(i - 5.5f) - distance)), green)
                pair(5, .25f, green); pair(6, .25f, green)
            }
            Kind.Shift -> {
                for (i in 4..7) {
                    dot(1, i, .85f * (1f - ease), blue)
                    dot(0, i, .28f + .55f * sin(ease * Math.PI).toFloat(), blue)
                }
                if (pulse.row == 1) dot(1, 5, .24f, blue)
            }
            Kind.Language, Kind.Layout -> {
                val offset = ease * 6f
                for (i in 0..11) {
                    dot(0, i, (1f - t) * max(0f, 1f - abs(i - 2f - offset)), purple)
                    dot(1, i, (1f - t) * max(0f, 1f - abs(i - 9f + offset)), purple)
                }
                pair(if (chinese) 2 else 9, .3f, purple)
            }
            Kind.Punctuation -> {
                dot(0, 5, .3f + .6f * sin(t * Math.PI).toFloat(), yellow)
                dot(1, 6, .3f + .6f * sin(t * Math.PI * 2).toFloat().coerceAtLeast(0f), yellow)
            }
            Kind.Error -> for (i in 4..7) pair(i, .3f + (1f - t) * .6f, red)
        }
        // New events crossfade from the actual displayed frame, including fast backspaces.
        if (!repeated) blendStart((t * 4f).coerceAtMost(1f))
    }

    private fun blendStart(weight: Float) {
        for (i in 0..23) {
            levels[i] = startLevels[i] * (1f - weight) + levels[i] * weight
            colors[i] = ColorUtils.blendARGB(startColors[i], colors[i], weight)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val d = density
        panel.set(4 * d, h * .47f, w - 4 * d, h - 7 * d)
        val pitch = (panel.width() - 9 * d) / 11f
        for (i in 0..23) {
            xs[i] = panel.left + 4.5f * d + (i % 12) * pitch
            ys[i] = panel.centerY() + if (i < 12) -3.3f * d else 3.3f * d
            glowShaders[i] = RadialGradient(xs[i], ys[i], 3.25f * d,
                intArrayOf(0xfffff0d6.toInt(), 0x00fff0d6), floatArrayOf(.185f, 1f), Shader.TileMode.CLAMP)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val d = density
        paint.style = Paint.Style.FILL; paint.color = panelColor
        canvas.drawRoundRect(panel, 4 * d, 4 * d, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = .5f * d; paint.color = border
        canvas.drawRoundRect(panel, 4 * d, 4 * d, paint)
        paint.style = Paint.Style.FILL
        val activeFrame = !idle && !returningToIdle && (playing || busy())
        for (i in 0..23) {
            val level = levels[i]
            if (activeFrame && level > .12f) {
                paint.color = Color.WHITE
                paint.alpha = (level * 135).toInt().coerceIn(0, 153)
                paint.shader = glowShaders[i]
                canvas.drawCircle(xs[i], ys[i], 3.75f * d, paint)
                paint.shader = null
                paint.alpha = 255
            }
            val color = if (dark) ColorUtils.blendARGB(colors[i], Color.WHITE, .18f) else colors[i]
            val gain = if (activeFrame) 1.8f else .85f
            paint.color = ColorUtils.blendARGB(unlit, color, (sqrt(level.coerceIn(0f, 1f)) * gain).coerceAtMost(1f))
            canvas.drawCircle(xs[i], ys[i], 1.6f * d, paint)
        }
        val lamp = when {
            task?.phase == Phase.Error -> 0
            busy() -> 1
            task != null -> 2
            !idle && (pulse.kind == Kind.Delete || pulse.kind == Kind.Error) -> 0
            !idle && (pulse.kind == Kind.Space || pulse.kind == Kind.Punctuation) -> 1
            else -> 2
        }
        val boost = if (!activeFrame) 0f else if (playing) sin(progress * Math.PI).toFloat().coerceAtLeast(0f) else 1f
        for (i in 0..2) {
            val x = width / 2f + (i - 1) * 10f * d
            val y = height * .26f
            paint.color = ColorUtils.blendARGB(lampColors[i], Color.WHITE, if (i == lamp) .14f * lampVisualGain * boost else 0f)
            canvas.drawCircle(x, y, 2.6f * d, paint)
            val highlight = 80 + if (i == lamp) (160 * boost).toInt() else 0
            paint.color = ColorUtils.setAlphaComponent(Color.WHITE, highlight)
            canvas.drawCircle(x - .65f * d, y - .8f * d, .65f * d, paint)
        }
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (!isVisible) reset()
    }

    override fun onDetachedFromWindow() { reset(); super.onDetachedFromWindow() }
}
