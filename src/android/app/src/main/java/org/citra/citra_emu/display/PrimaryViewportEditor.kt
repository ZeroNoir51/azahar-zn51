// Copyright 2026 Azahar Emulator Project
// Licensed under GPLv3 or any later version. Refer to LICENSE.txt.

// Fork modification notice: ZeroNoir51/azahar-zn51, 2026-10-27 (first publication).
// Added touch-based positioning, resizing, and persistent placement of the primary display viewport.
// Fork modifications are licensed under GPLv3 or any later version; see LICENSE.txt.
// Modified 2026-10-27 by ZeroNoir51: dual-display editing and aspect presets.
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

/** Both displays are controlled from the primary touchscreen; external changes preview live. */
class PrimaryViewportEditor(
    private val surface: SurfaceView,
    private val input: View,
    private val parent: ViewGroup,
    private val backDispatcher: OnBackPressedDispatcher,
    private val secondary: SecondaryDisplay,
) {
    private val context = surface.context
    private val primary = ViewportTransform(surface, "primary_viewport_v1")
    private var target = primary
    private val snapshots = linkedMapOf<ViewportTransform, ViewportPlacement>()
    private var editor: FrameLayout? = null
    private var handles: Handles? = null
    private var hint: TextView? = null
    private var ratioButton: Button? = null
    private var primaryButton: Button? = null
    private var externalButton: Button? = null
    private var back: OnBackPressedCallback? = null
    private val surfaceLocation = IntArray(2)
    private val inputLocation = IntArray(2)

    init {
        secondary.onViewportChanged = { finish(false) }
    }

    /** Inverse primary transform, including nonuniform aspect presets. */
    fun mapTouch(x: Float, y: Float): Pair<Float, Float>? {
        if (editor != null || !primary.ready) return null
        surface.getLocationInWindow(surfaceLocation)
        input.getLocationInWindow(inputLocation)
        val localX = (x + inputLocation[0] - surfaceLocation[0]) / primary.placement.width
        val localY = (y + inputLocation[1] - surfaceLocation[1]) / primary.placement.height
        return if (localX >= 0f && localY >= 0f && localX < surface.width && localY < surface.height) {
            Pair(localX, localY)
        } else null
    }

    fun show() {
        if (editor != null || !primary.ready || !NativeLibrary.isRunning()) return
        NativeLibrary.onTouchEvent(0f, 0f, false)
        NativeLibrary.onSecondaryTouchEvent(0f, 0f, false)
        target = primary
        snapshots.clear()
        listOfNotNull(primary, secondary.viewport?.takeIf { it.ready }).forEach {
            snapshots[it] = it.placement
            it.editing = true
            it.onGeometryChange = { finish(false) }
        }
        val layer = FrameLayout(context)
        editor = layer
        handles = Handles(context).also { layer.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        val heading = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xEE101820.toInt())
        }
        val selectors = LinearLayout(context).apply { gravity = Gravity.CENTER }
        primaryButton = Button(context).apply {
            setText(R.string.viewport_internal)
            setOnClickListener { select(primary) }
        }.also { selectors.addView(it) }
        externalButton = Button(context).apply {
            setText(R.string.viewport_external)
            isEnabled = snapshots.size > 1
            setOnClickListener { snapshots.keys.firstOrNull { it !== primary }?.let { select(it) } }
        }.also { selectors.addView(it) }
        heading.addView(selectors)
        hint = TextView(context).apply {
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }.also { heading.addView(it) }
        layer.addView(heading, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xEE101820.toInt())
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        fun button(label: Int, action: () -> Unit): Button = Button(context).apply {
            setText(label)
            setOnClickListener { handles?.cancelDrag(); action() }
            actions.addView(this)
        }
        ratioButton = button(R.string.viewport_ratio_internal) {
            target.placement = target.placement.withAspect(
                if (target === primary) 4f / 3f else 15f / 9f,
                target.surface.width, target.surface.height,
            )
            handles?.invalidate()
        }
        button(R.string.primary_viewport_center) {
            target.placement = target.placement.centered(); handles?.invalidate()
        }
        button(R.string.primary_viewport_reset) {
            target.placement = ViewportPlacement(); handles?.invalidate()
        }
        button(android.R.string.cancel) { finish(false) }
        button(R.string.primary_viewport_save) { finish(true) }
        val scroll = HorizontalScrollView(context).apply { addView(actions) }
        layer.addView(scroll, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        parent.addView(layer, ViewGroup.LayoutParams(-1, -1))
        back = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish(false)
        }.also { backDispatcher.addCallback(it) }
        select(primary)
    }

    private fun select(viewport: ViewportTransform) {
        handles?.cancelDrag()
        target = viewport
        primaryButton?.isEnabled = target !== primary
        externalButton?.isEnabled = snapshots.size > 1 && target === primary
        ratioButton?.setText(if (target === primary) R.string.viewport_ratio_internal else R.string.viewport_ratio_external)
        hint?.setText(if (target === primary) R.string.primary_viewport_hint else R.string.viewport_external_hint)
        handles?.invalidate()
    }

    private fun finish(save: Boolean) {
        val layer = editor ?: return
        editor = null
        snapshots.forEach { (viewport, previous) ->
            viewport.onGeometryChange = null
            viewport.editing = false
            if (save) viewport.save() else viewport.placement = previous
        }
        snapshots.clear()
        target = primary
        NativeLibrary.onSecondaryTouchEvent(0f, 0f, false)
        parent.removeView(layer)
        handles = null
        hint = null
        ratioButton = null
        primaryButton = null
        externalButton = null
        back?.remove()
        back = null
    }

    fun dispose() {
        finish(false)
        secondary.onViewportChanged = null
        primary.dispose()
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private inner class Handles(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var start = ViewportPlacement()
        private var downX = 0f
        private var downY = 0f
        private var corner = -1
        private var dragging = false
        private var pointer = -1

        fun cancelDrag() {
            dragging = false
            pointer = -1
            parent.requestDisallowInterceptTouchEvent(false)
        }

        // A scaled map of the external display allows control without external touch support.
        private fun screenBounds(): RectF {
            if (target === primary) return RectF(surface.left.toFloat(), surface.top.toFloat(),
                (surface.left + surface.width).toFloat(), (surface.top + surface.height).toFloat())
            val availableWidth = (width - dp(32)).coerceAtLeast(1).toFloat()
            val availableHeight = (height - dp(200)).coerceAtLeast(1).toFloat()
            val scale = min(availableWidth / target.surface.width, availableHeight / target.surface.height)
            val w = target.surface.width * scale
            val h = target.surface.height * scale
            val left = (width - w) / 2f
            val top = dp(112) + (availableHeight - h) / 2f
            return RectF(left, top, left + w, top + h)
        }

        private fun bounds(): RectF {
            val screen = screenBounds()
            val p = target.placement
            return RectF(screen.left + p.x * screen.width(), screen.top + p.y * screen.height(),
                screen.left + (p.x + p.width) * screen.width(), screen.top + (p.y + p.height) * screen.height())
        }

        override fun onDraw(canvas: Canvas) {
            if (target !== primary) {
                canvas.drawColor(0xF0101820.toInt())
                paint.color = Color.DKGRAY
                paint.style = Paint.Style.FILL
                canvas.drawRect(screenBounds(), paint)
            }
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
                    start = target.placement
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
                    val screen = screenBounds()
                    val dx = (event.getX(index) - downX) / screen.width()
                    val dy = (event.getY(index) - downY) / screen.height()
                    target.placement = if (corner < 0) start.moved(dx, dy) else start.resized(corner, dx, dy)
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelDrag()
                    if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.getPointerId(event.actionIndex) == pointer) cancelDrag()
                }
            }
            return true
        }
    }
}
