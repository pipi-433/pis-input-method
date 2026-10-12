/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.pis

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView
import kotlin.math.abs

/** Capture the candidate/input session at touch-down; only a completed upward swipe applies it. */
class PisCandidateSwipeView(context: Context) : CustomGestureView(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val distance = 24 * resources.displayMetrics.density
    private var startX = 0f
    private var startY = 0f
    private var moved = false
    private var canceled = false
    private var held = false
    private var completion: (() -> Unit)? = null

    var upActionFactory: (() -> (() -> Unit)?)? = null
        set(value) {
            // Rebinding also ends the superclass's click/long-press state.
            canceled = true
            if (swipeEnabled) {
                val cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
                try { super.onTouchEvent(cancel) } finally { cancel.recycle() }
            }
            field = value
            completion = null // A recycled/rebound row cannot finish the old candidate's swipe.
            swipeEnabled = value != null
            swipeThresholdX = distance
            swipeThresholdY = distance
            onGestureListener = if (value == null) null else OnGestureListener { _, _ -> moved || canceled || held }
        }

    override fun performLongClick(): Boolean {
        if (upActionFactory != null && (moved || canceled)) return false
        return super.performLongClick().also { if (it) held = true }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (upActionFactory == null) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x; startY = event.y
                moved = false; canceled = false; held = false
                completion = upActionFactory?.invoke()
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - startX) > slop || abs(event.y - startY) > slop) moved = true
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> canceled = true
        }
        if (event.pointerCount != 1) canceled = true
        if (event.actionMasked == MotionEvent.ACTION_UP &&
            (abs(event.x - startX) > slop || abs(event.y - startY) > slop)) moved = true
        val action = completion
        val upward = event.actionMasked == MotionEvent.ACTION_UP && !canceled && !held &&
            PisCandidateEnglish.isUpward(event.x - startX, event.y - startY, distance)
        // Consumed movement prevents the superclass from selecting the Chinese word on release.
        if (event.actionMasked == MotionEvent.ACTION_UP && upward) moved = true
        val handled = super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            completion = null
            if (upward) action?.invoke()
        }
        return handled
    }

    override fun onDetachedFromWindow() {
        completion = null; canceled = true
        super.onDetachedFromWindow()
    }

}
