// Copyright 2026 ZeroNoir51
// Licensed under GPLv3 or any later version; see LICENSE.txt.
// Added 2026-10-27 for independent internal/external viewport geometry.
package org.citra.citra_emu.display

import kotlin.math.min

/** Fractions of the untransformed surface, independent of display resolution. */
internal data class ViewportPlacement(
    val width: Float = 1f,
    val height: Float = 1f,
    val x: Float = 0f,
    val y: Float = 0f,
) {
    fun bounded(): ViewportPlacement {
        fun finite(value: Float, fallback: Float) = if (value.isFinite()) value else fallback
        val w = finite(width, 1f).coerceIn(0.01f, 1f)
        val h = finite(height, 1f).coerceIn(0.01f, 1f)
        return ViewportPlacement(w, h, finite(x, 0f).coerceIn(0f, 1f - w),
            finite(y, 0f).coerceIn(0f, 1f - h))
    }

    fun centered() = copy(x = (1f - width) / 2f, y = (1f - height) / 2f)

    fun withAspect(ratio: Float, surfaceWidth: Int, surfaceHeight: Int): ViewportPlacement {
        if (surfaceWidth <= 0 || surfaceHeight <= 0 || !ratio.isFinite() || ratio <= 0f) return this
        var w = width
        var h = w * surfaceWidth / (ratio * surfaceHeight)
        // Preserve width unless the requested height cannot fit the display.
        if (h > 1f) { w /= h; h = 1f }
        return ViewportPlacement(w, h, x + (width - w) / 2f, y + (height - h) / 2f).bounded()
    }

    fun moved(dx: Float, dy: Float) = copy(x = x + dx, y = y + dy).bounded()

    fun resized(corner: Int, dx: Float, dy: Float): ViewportPlacement {
        val left = corner % 2 == 0
        val top = corner < 2
        val ax = if (left) x + width else x
        val ay = if (top) y + height else y
        val maximum = min((if (left) ax else 1f - ax) / width,
            (if (top) ay else 1f - ay) / height)
        val delta = ((if (left) -dx else dx) / width + (if (top) -dy else dy) / height) / 2f
        val factor = (1f + delta).coerceIn(min(0.25f / width, maximum), maximum)
        val w = width * factor
        val h = height * factor
        return ViewportPlacement(w, h, if (left) ax - w else ax, if (top) ay - h else ay).bounded()
    }
}
