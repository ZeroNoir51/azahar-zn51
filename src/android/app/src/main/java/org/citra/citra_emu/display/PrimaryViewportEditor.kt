// Copyright 2026 Azahar Emulator Project
// Licensed under GPLv3 or any later version. Refer to LICENSE.txt.

// Fork modification notice: ZeroNoir51/azahar-zn51, 2026-10-27 (first publication).
// Added touch-based positioning, resizing, and persistent placement of the primary display viewport.
// Fork modifications are licensed under GPLv3 or any later version; see LICENSE.txt.
package org.citra.citra_emu.display

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.R
import kotlin.math.min

/** Transforms only the primary surface; its framebuffer and the external display stay intact. */
class PrimaryViewportEditor(
    private val surface: SurfaceView,
    private val input: View,
    private val parent: ViewGroup,
    private val backDispatcher: OnBackPressedDispatcher,
) {
    private data class Placement(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f)
    private val context = surface.context
    private val preferences = context.getSharedPreferences("primary_viewport_v1", Context.MODE_PRIVATE)
    private var placement = Placement()
    private var saved = placement
    private var profile = ""
    private var editor: FrameLayout? = null
    private var handles: Handles? = null
    private var back: OnBackPressedCallback? = null
    private val surfaceLocation = IntArray(2)
    private val inputLocation = IntArray(2)
    private val sizeListener = View.OnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
        if (r - l != or - ol || b - t != ob - ot) {
            // A rotation/window resize cancels the unfinished edit before loading its profile.
            finish(false)
            load()
        }
    }

    init {
        surface.addOnLayoutChangeListener(sizeListener)
        if (surface.width > 0 && surface.height > 0) load()
    }

    private fun load() {
        profile = if (surface.width >= surface.height) "landscape" else "portrait"
        fun value(key: String, default: Float): Float =
            preferences.getFloat("${profile}_$key", default).let { if (it.isFinite()) it else default }
        val scale = value("scale", 1f).coerceIn(0.25f, 1f)
        placement = Placement(scale, value("x", 0f).coerceIn(0f, 1f - scale),
            value("y", 0f).coerceIn(0f, 1f - scale))
        applyPlacement()
    }

    private fun applyPlacement() {
        surface.pivotX = 0f
        surface.pivotY = 0f
        surface.scaleX = placement.scale
        surface.scaleY = placement.scale
        surface.translationX = placement.x * surface.width
        surface.translationY = placement.y * surface.height
        handles?.invalidate()
    }

    /** Inverse surface transform. Coordinates outside the viewport must not reach the 3DS. */
    fun mapTouch(x: Float, y: Float): Pair<Float, Float>? {
        if (editor != null || surface.width == 0 || surface.height == 0) return null
        surface.getLocationInWindow(surfaceLocation)
        input.getLocationInWindow(inputLocation)
        val localX = (x + inputLocation[0] - surfaceLocation[0]) / placement.scale
        val localY = (y + inputLocation[1] - surfaceLocation[1]) / placement.scale
        return if (localX >= 0f && localY >= 0f && localX < surface.width && localY < surface.height) {
            Pair(localX, localY)
        } else null
    }

    fun show() {
        if (editor != null || surface.width == 0 || surface.height == 0 || !NativeLibrary.isRunning()) return
        NativeLibrary.onTouchEvent(0f, 0f, false)
        saved = placement
        val layer = FrameLayout(context)
        editor = layer
        handles = Handles(context).also {
            layer.addView(it, FrameLayout.LayoutParams(-1, -1))
        }
        val hint = TextView(context).apply {
            setText(R.string.primary_viewport_hint)
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC101820.toInt())
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        layer.addView(hint, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xEE101820.toInt())
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        fun button(label: Int, action: () -> Unit) {
            actions.addView(Button(context).apply {
                setText(label)
                setOnClickListener { action() }
            })
        }
        button(R.string.primary_viewport_center) {
            placement = placement.copy(x = (1f - placement.scale) / 2f, y = (1f - placement.scale) / 2f)
            applyPlacement()
        }
        button(R.string.primary_viewport_reset) { placement = Placement(); applyPlacement() }
        button(android.R.string.cancel) { finish(false) }
        button(R.string.primary_viewport_save) { finish(true) }
        val scroll = HorizontalScrollView(context).apply { addView(actions) }
        layer.addView(scroll, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        parent.addView(layer, ViewGroup.LayoutParams(-1, -1))
        back = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish(false)
        }.also { backDispatcher.addCallback(it) }
    }

    private fun finish(save: Boolean) {
        val layer = editor ?: return
        if (save) {
            preferences.edit().putFloat("${profile}_scale", placement.scale)
                .putFloat("${profile}_x", placement.x).putFloat("${profile}_y", placement.y).apply()
        } else {
            placement = saved
        }
        parent.removeView(layer)
        editor = null
        handles = null
        back?.remove()
        back = null
        applyPlacement()
    }

    fun dispose() {
        finish(false)
        surface.removeOnLayoutChangeListener(sizeListener)
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private inner class Handles(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var start = Placement()
        private var downX = 0f
        private var downY = 0f
        private var corner = -1
        private var dragging = false
        private var pointer = -1

        private fun bounds(): RectF = RectF(
            surface.left + placement.x * surface.width,
            surface.top + placement.y * surface.height,
            surface.left + (placement.x + placement.scale) * surface.width,
            surface.top + (placement.y + placement.scale) * surface.height,
        )

        override fun onDraw(canvas: Canvas) {
            val rect = bounds()
            paint.color = Color.CYAN
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(2).toFloat()
            canvas.drawRect(rect, paint)
            paint.style = Paint.Style.FILL
            for ((x, y) in corners(rect)) canvas.drawCircle(x, y, dp(9).toFloat(), paint)
        }

        private fun corners(rect: RectF) = listOf(
            Pair(rect.left, rect.top), Pair(rect.right, rect.top),
            Pair(rect.left, rect.bottom), Pair(rect.right, rect.bottom),
        )

        override fun performClick(): Boolean { super.performClick(); return true }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pointer = event.getPointerId(0)
                    downX = event.x
                    downY = event.y
                    start = placement
                    val rect = bounds()
                    corner = corners(rect).indexOfFirst { (x, y) ->
                        kotlin.math.abs(event.x - x) <= dp(28) && kotlin.math.abs(event.y - y) <= dp(28)
                    }
                    dragging = rect.contains(event.x, event.y) || corner >= 0
                    parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val index = event.findPointerIndex(pointer)
                    if (!dragging || index < 0) return true
                    val dx = (event.getX(index) - downX) / surface.width
                    val dy = (event.getY(index) - downY) / surface.height
                    if (corner < 0) {
                        placement = start.copy(x = (start.x + dx).coerceIn(0f, 1f - start.scale),
                            y = (start.y + dy).coerceIn(0f, 1f - start.scale))
                    } else {
                        val left = corner % 2 == 0
                        val top = corner < 2
                        val anchorX = if (left) start.x + start.scale else start.x
                        val anchorY = if (top) start.y + start.scale else start.y
                        val maxScale = min(if (left) anchorX else 1f - anchorX,
                            if (top) anchorY else 1f - anchorY)
                        val change = ((if (left) -dx else dx) + (if (top) -dy else dy)) / 2f
                        val scale = (start.scale + change).coerceIn(min(0.25f, maxScale), maxScale)
                        placement = Placement(scale, if (left) anchorX - scale else anchorX,
                            if (top) anchorY - scale else anchorY)
                    }
                    applyPlacement()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    pointer = -1
                    parent.requestDisallowInterceptTouchEvent(false)
                    if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.getPointerId(event.actionIndex) == pointer) {
                        dragging = false
                        pointer = -1
                    }
                }
            }
            return true
        }
    }
}
