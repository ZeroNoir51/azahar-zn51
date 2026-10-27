// Copyright 2026 ZeroNoir51
// Licensed under GPLv3 or any later version; see LICENSE.txt.
// Added 2026-10-27: standalone geometry regression checks (no Android runtime required).
package org.citra.citra_emu.display

import kotlin.math.abs
import kotlin.random.Random

private fun near(actual: Float, expected: Float) {
    check(abs(actual - expected) < 0.0001f) { "$actual != $expected" }
}

private fun inside(p: ViewportPlacement) {
    check(listOf(p.width, p.height, p.x, p.y).all { it.isFinite() })
    check(p.width > 0f && p.height > 0f && p.x >= 0f && p.y >= 0f)
    check(p.x + p.width <= 1.00001f && p.y + p.height <= 1.00001f)
}

fun main() {
    val original = ViewportPlacement(0.6f, 0.6f, 0.2f, 0.1f)
    val internal = original.withAspect(4f / 3f, 1920, 1080)
    near(internal.width, original.width)
    near(internal.width * 1920 / (internal.height * 1080), 4f / 3f)
    val external = original.withAspect(15f / 9f, 1920, 1080)
    near(external.width, original.width)
    near(external.width * 1920 / (external.height * 1080), 15f / 9f)
    // Full-width 4:3 cannot fit inside 16:9 without reducing the width.
    val fit = ViewportPlacement().withAspect(4f / 3f, 1920, 1080)
    near(fit.width, 0.75f); near(fit.height, 1f); near(fit.x, 0.125f)
    val center = ViewportPlacement(0.5f, 0.5f, 0.25f, 0.25f).withAspect(4f / 3f, 1920, 1080)
    near(center.x + center.width / 2, 0.5f)
    near(center.y + center.height / 2, 0.5f)
    check(original.withAspect(0f, 1920, 1080) == original)
    check(original.withAspect(4f / 3f, 0, 1080) == original)
    inside(ViewportPlacement(Float.NaN, Float.POSITIVE_INFINITY, -10f, 10f).bounded())
    val random = Random(51)
    repeat(10000) {
        val p = ViewportPlacement(random.nextFloat() * 0.75f + 0.25f,
            random.nextFloat() * 0.75f + 0.25f, random.nextFloat(), random.nextFloat()).bounded()
        val ratio = if (it % 2 == 0) 4f / 3f else 15f / 9f
        val sw = if (it % 3 == 0) 1080 else 1920
        val sh = if (it % 3 == 0) 1920 else 1080
        val a = p.withAspect(ratio, sw, sh)
        inside(a)
        near(a.width * sw / (a.height * sh), ratio)
        inside(a.moved(random.nextFloat() * 4f - 2f, random.nextFloat() * 4f - 2f))
        for (corner in 0..3) {
            val resized = a.resized(corner, random.nextFloat() * 2f - 1f, random.nextFloat() * 2f - 1f)
            inside(resized)
            near(resized.width / resized.height, a.width / a.height)
            near(if (corner % 2 == 0) resized.x + resized.width else resized.x,
                if (corner % 2 == 0) a.x + a.width else a.x)
            near(if (corner < 2) resized.y + resized.height else resized.y,
                if (corner < 2) a.y + a.height else a.y)
        }
    }
    println("Viewport geometry: presets, fit, centering, bounds and 40,000 corner resizes passed")
}
