// Copyright 2026 ZeroNoir51
// Licensed under GPLv3 or any later version; see LICENSE.txt.
// Added 2026-10-27: independent persistent transforms, including legacy primary placements.
package org.citra.citra_emu.display

import android.content.Context
import android.view.SurfaceView
import android.view.View

internal class ViewportTransform(val surface: SurfaceView, preferenceName: String) {
    private val preferences = surface.context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
    private var profile = ""
    var editing = false
    var onGeometryChange: (() -> Unit)? = null
    var placement = ViewportPlacement()
        set(value) { field = value.bounded(); applyPlacement() }
    val ready get() = surface.width > 0 && surface.height > 0
    private val sizeListener = View.OnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
        if (r - l != or - ol || b - t != ob - ot) {
            onGeometryChange?.invoke()
            load()
        }
    }

    init {
        surface.addOnLayoutChangeListener(sizeListener)
        if (ready) load()
    }

    private fun load() {
        if (!ready) return
        profile = if (surface.width >= surface.height) "landscape" else "portrait"
        // The first editor stored one uniform scale. Keep those saved placements intact.
        val legacy = preferences.getFloat("${profile}_scale", 1f)
        placement = ViewportPlacement(
            preferences.getFloat("${profile}_width", legacy),
            preferences.getFloat("${profile}_height", legacy),
            preferences.getFloat("${profile}_x", 0f),
            preferences.getFloat("${profile}_y", 0f),
        )
    }

    fun save() {
        if (!ready) return
        preferences.edit().putFloat("${profile}_width", placement.width)
            .putFloat("${profile}_height", placement.height)
            .putFloat("${profile}_x", placement.x).putFloat("${profile}_y", placement.y).apply()
    }

    private fun applyPlacement() {
        surface.pivotX = 0f
        surface.pivotY = 0f
        surface.scaleX = placement.width
        surface.scaleY = placement.height
        surface.translationX = placement.x * surface.width
        surface.translationY = placement.y * surface.height
    }

    fun dispose() {
        onGeometryChange?.invoke()
        onGeometryChange = null
        surface.removeOnLayoutChangeListener(sizeListener)
    }
}
