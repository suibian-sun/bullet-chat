package com.suibian.bulletchat.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.TextView

object Palette {
    const val BG = 0xFF080C12.toInt()
    const val SURFACE = 0xFF101823.toInt()
    const val SURFACE_2 = 0xFF18222F.toInt()
    const val TEXT = 0xFFE8EFF7.toInt()
    const val MUTED = 0xFF93A3B8.toInt()
    const val ACCENT = 0xFF5FE3F3.toInt()
    const val ACCENT_DARK = 0xFF00343B.toInt()
    const val SUCCESS = 0xFF4ADE80.toInt()
    const val WARN = 0xFFFBBF24.toInt()
    const val OUTLINE = 0xFF2B3949.toInt()
    const val HERO_START = 0xFF123244.toInt()
    const val HERO_END = 0xFF0B292B.toInt()
}

fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()
fun Context.dp(value: Int): Int = dp(value.toFloat())

fun Context.roundedBackground(
    color: Int,
    radiusDp: Float = 20f,
    strokeColor: Int? = null,
    strokeWidthDp: Float = 1f,
): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = dp(radiusDp).toFloat()
    setColor(color)
    if (strokeColor != null) setStroke(dp(strokeWidthDp), strokeColor)
}

fun Context.textView(
    text: String,
    sizeSp: Float,
    color: Int = Palette.TEXT,
    bold: Boolean = false,
): TextView = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    setTextColor(color)
    if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    includeFontPadding = false
}

fun Context.hairline(color: Int = 0x332B3949): android.view.View =
    android.view.View(this).apply { setBackgroundColor(color) }

fun parseColor(hex: String): Int = Color.parseColor(hex)
