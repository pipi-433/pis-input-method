/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView.OnGestureListener

open class CustomGestureView(ctx: Context) : FrameLayout(ctx) {

    enum class SwipeAxis { X, Y }

    enum class GestureType { Down, Move, Up }

    data class Event(
        val type: GestureType,
        val consumed: Boolean,
        val x: Float,
        val y: Float,
        val countX: Int,
        val countY: Int,
        val totalX: Int,
        val totalY: Int
    )

    fun interface OnGestureListener {
        fun onGesture(view: View, event: Event): Boolean

        companion object {
            val Empty = OnGestureListener { _, _ -> false }
        }
    }

    private val lifecycleScope by lazy {
        findViewTreeLifecycleOwner()?.lifecycleScope!!
    }

    @Volatile
    private var touchMovedOutside = false

    @Volatile
    private var longPressTriggered = false
    var longPressEnabled = false
    private var longPressJob: Job? = null

    @Volatile
    var longPressFeedbackEnabled = true

    @Volatile
    private var repeatStarted = false
    var repeatEnabled = false
    private var repeatJob: Job? = null

    var swipeEnabled = false
    var swipeRepeatEnabled = false
    var allowSwipeAfterRepeat = false
    var swipeThresholdX = 24f
    var swipeThresholdY = 24f

    private var swipeRepeatTriggered = false
    private var swipeLastX = -1f
    private var swipeLastY = -1f
    private var swipeXUnconsumed = 0f
    private var swipeYUnconsumed = 0f
    private var swipeTotalX = 0
    private var swipeTotalY = 0
    private var gestureConsumed = false

    var doubleTapEnabled = false
    private var lastClickTime = 0L
    private var maybeDoubleTap = false

    var onDoubleTapListener: ((View) -> Unit)? = null
    var onRepeatListener: ((View) -> Unit)? = null
    var onGestureListener: OnGestureListener? = null
    var onHoldFinished: ((Boolean) -> Unit)? = null
    var orderedCommitEnabled = false
    var clickOnDown = false
    var onPointerActiveChange: ((CustomGestureView, Boolean) -> Unit)? = null
    private var pointerActive = false
    private var ownedPointerId = -1
    private var earlyReleased = false
    private var downClickDispatched = false
    private var longPressAttempted = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private fun setPointerActive(active: Boolean) {
        if (pointerActive == active) return
        pointerActive = active
        onPointerActiveChange?.invoke(this, active)
    }

    /** Finish an ordinary earlier key once, before the next finger is delivered. */
    fun finishForNextPointer(): Boolean {
        if (!pointerActive || earlyReleased || !isEnabled) return false
        if (repeatEnabled && downClickDispatched) {
            earlyReleased = true
            isPressed = false
            setPointerActive(false)
            dispatchGestureEvent(GestureType.Up, lastTouchX, lastTouchY)
            resetState()
            return true
        }
        if (!orderedCommitEnabled || touchMovedOutside || longPressAttempted || longPressTriggered ||
            repeatEnabled || swipeRepeatTriggered || gestureConsumed || swipeTotalX != 0 || swipeTotalY != 0) return false
        earlyReleased = true
        isPressed = false
        setPointerActive(false)
        dispatchGestureEvent(GestureType.Up, lastTouchX, lastTouchY)
        val click = !gestureConsumed
        resetState()
        if (click) performClick()
        return click
    }
    private var holdFinished = false
    private fun finishHold(canceled: Boolean) {
        if (longPressTriggered && !holdFinished) { holdFinished = true; onHoldFinished?.invoke(canceled) }
    }

    var soundEffect: InputFeedbacks.SoundEffect = InputFeedbacks.SoundEffect.Standard

    private val touchSlop: Float = ViewConfiguration.get(ctx).scaledTouchSlop.toFloat()

    init {
        // disable system sound effect and haptic feedback
        isSoundEffectsEnabled = false
        isHapticFeedbackEnabled = false
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) {
            isPressed = false
        }
    }

    private fun pointInView(x: Float, y: Float): Boolean {
        return -touchSlop <= x &&
                -touchSlop <= y &&
                x < (width + touchSlop) &&
                y < (height + touchSlop)
    }

    private fun resetState() {
        touchMovedOutside = false
        if (longPressEnabled) {
            longPressTriggered = false
            longPressJob?.cancel()
            longPressJob = null
        }
        if (repeatEnabled) {
            repeatStarted = false
            repeatJob?.cancel()
            repeatJob = null
        }
        if (swipeEnabled) {
            if (swipeRepeatEnabled) {
                swipeRepeatTriggered = false
            }
            swipeXUnconsumed = 0f
            swipeYUnconsumed = 0f
            swipeTotalX = 0
            swipeTotalY = 0
            gestureConsumed = false
        }
        // double tap state should be preserved on touch up
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        var action = event.actionMasked
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            if (pointerActive) return true
            action = MotionEvent.ACTION_DOWN
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            if (ownedPointerId != event.getPointerId(event.actionIndex)) return true
            action = MotionEvent.ACTION_UP
        }
        if (action == MotionEvent.ACTION_DOWN && pointerActive &&
            ownedPointerId != event.getPointerId(event.actionIndex)) return true
        val index = if (action == MotionEvent.ACTION_DOWN) event.actionIndex else event.findPointerIndex(ownedPointerId)
        if (index < 0 && action != MotionEvent.ACTION_CANCEL) return true
        val x = if (index >= 0) event.getX(index) else lastTouchX
        val y = if (index >= 0) event.getY(index) else lastTouchY
        lastTouchX = x; lastTouchY = y
        if (earlyReleased && action != MotionEvent.ACTION_DOWN) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                resetState(); ownedPointerId = -1; setPointerActive(false)
            }
            return true
        }
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (!isEnabled) return false
                resetState()
                holdFinished = false; earlyReleased = false; downClickDispatched = false; longPressAttempted = false
                ownedPointerId = event.getPointerId(event.actionIndex)
                setPointerActive(true)
                drawableHotspotChanged(x, y)
                isPressed = true
                dispatchGestureEvent(GestureType.Down, x, y)
                if (clickOnDown) {
                    downClickDispatched = true
                    performClick()
                }
                InputFeedbacks.hapticFeedback(this)
                InputFeedbacks.soundEffect(soundEffect)
                if (longPressEnabled) {
                    longPressJob?.cancel()
                    longPressJob = lifecycleScope.launch {
                        delay(longPressDelay.toLong())
                        if (!pointerActive || earlyReleased || !isEnabled) return@launch
                        longPressAttempted = true
                        if (longPressFeedbackEnabled) {
                            InputFeedbacks.hapticFeedback(this@CustomGestureView, true)
                        }
                        longPressTriggered = performLongClick()
                    }
                }
                if (repeatEnabled) {
                    repeatJob?.cancel()
                    repeatJob = lifecycleScope.launch {
                        delay(longPressDelay.toLong())
                        repeatStarted = true
                        var lastTriggerTime: Long
                        while (isActive && isEnabled && pointerActive && !earlyReleased) {
                            lastTriggerTime = SystemClock.uptimeMillis()
                            onRepeatListener?.invoke(this@CustomGestureView)
                            val t = lastTriggerTime + RepeatInterval - SystemClock.uptimeMillis()
                            if (t > 0) delay(t)
                        }
                    }
                }
                if (swipeEnabled) {
                    swipeLastX = x
                    swipeLastY = y
                }
            }
            MotionEvent.ACTION_UP -> {
                setPointerActive(false)
                finishHold(false)
                isPressed = false
                dispatchGestureEvent(GestureType.Up, x, y)
                val shouldPerformClick = !(touchMovedOutside ||
                        downClickDispatched ||
                        longPressTriggered ||
                        repeatStarted ||
                        swipeRepeatTriggered ||
                        gestureConsumed)
                resetState()
                ownedPointerId = -1
                if (shouldPerformClick) {
                    if (doubleTapEnabled) {
                        val now = System.currentTimeMillis()
                        if (maybeDoubleTap && now - lastClickTime <= longPressDelay) {
                            maybeDoubleTap = false
                            onDoubleTapListener?.invoke(this)
                        } else {
                            maybeDoubleTap = true
                            performClick()
                        }
                        lastClickTime = now
                    } else {
                        performClick()
                    }
                }
                // Release feedback must not precede text dispatch (some devices use a Binder call).
                InputFeedbacks.hapticFeedback(this, longPress = true, keyUp = true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (longPressTriggered && onHoldFinished != null) {
                    if (y < -60 * resources.displayMetrics.density) finishHold(true)
                    return true
                }
                if (!isEnabled) return false
                drawableHotspotChanged(x, y)
                if (!touchMovedOutside && !pointInView(x, y)) {
                    touchMovedOutside = true
                    if (longPressEnabled) {
                        longPressJob?.cancel()
                        longPressJob = null
                    }
                    if (repeatEnabled) {
                        repeatJob?.cancel()
                        repeatJob = null
                    }
                    if (repeatStarted || !swipeEnabled) {
                        isPressed = false
                    }
                }
                if (!swipeEnabled || longPressTriggered || repeatStarted && !allowSwipeAfterRepeat) return true
                val countX = consumeSwipe(x, SwipeAxis.X)
                val countY = consumeSwipe(y, SwipeAxis.Y)
                dispatchGestureEvent(GestureType.Move, x, y, countX, countY)
                swipeLastX = x
                swipeLastY = y
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                setPointerActive(false)
                finishHold(true)
                isPressed = false
                dispatchGestureEvent(GestureType.Up, x, y)
                resetState()
                ownedPointerId = -1
                // reset double tap state on cancel
                if (doubleTapEnabled) {
                    maybeDoubleTap = false
                    lastClickTime = 0
                }
                return true
            }
        }
        return true
    }
    override fun onDetachedFromWindow() {
        setPointerActive(false)
        finishHold(true)
        resetState()
        ownedPointerId = -1
        super.onDetachedFromWindow()
    }

    private fun dispatchGestureEvent(
        type: GestureType,
        x: Float,
        y: Float,
        countX: Int = 0,
        countY: Int = 0
    ) {
        val event = Event(type, gestureConsumed, x, y, countX, countY, swipeTotalX, swipeTotalY)
        val consumed = onGestureListener?.onGesture(this, event) ?: return
        if (consumed && !gestureConsumed) {
            gestureConsumed = true
        }
    }

    private fun consumeSwipe(current: Float, axis: SwipeAxis): Int {
        val unconsumed: Float
        val threshold: Float
        when (axis) {
            SwipeAxis.X -> {
                unconsumed = current - swipeLastX + swipeXUnconsumed
                threshold = swipeThresholdX
            }
            SwipeAxis.Y -> {
                unconsumed = current - swipeLastY + swipeYUnconsumed
                threshold = swipeThresholdY
            }
        }
        val remains: Float = unconsumed % threshold
        val count: Int = (unconsumed / threshold).toInt()
        if (count != 0) {
            if (swipeRepeatEnabled && !swipeRepeatTriggered) {
                swipeRepeatTriggered = true
            }
            if (longPressEnabled && !longPressTriggered) {
                longPressJob?.cancel()
                longPressJob = null
            }
            if (repeatEnabled && !repeatStarted) {
                repeatJob?.cancel()
                repeatJob = null
            }
        }
        when (axis) {
            SwipeAxis.X -> {
                swipeXUnconsumed = remains
                swipeTotalX += count
            }
            SwipeAxis.Y -> {
                swipeYUnconsumed = remains
                swipeTotalY += count
            }
        }
        return count
    }

    override fun setOnLongClickListener(l: OnLongClickListener?) {
        longPressEnabled = l != null
        super.setOnLongClickListener(l)
    }

    companion object {
        val longPressDelay by AppPrefs.getInstance().keyboard.longPressDelay
        const val RepeatInterval = 50L
    }
}
