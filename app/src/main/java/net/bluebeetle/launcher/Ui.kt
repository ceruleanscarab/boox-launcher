package net.bluebeetle.launcher

import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

const val INK = Color.BLACK
const val PAPER = Color.WHITE
val SOFT_INK = Color.rgb(85, 85, 85)

/** Extra saturation for Kaleido colour, which washes everything out. */
val COLOR_BOOST = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.4f) })

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.box(strokeDp: Int = 1, fill: Int = PAPER, radiusDp: Int = 8, stroke: Int = INK) =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeDp > 0) setStroke(dp(strokeDp), stroke)
    }

fun Context.label(
    text: CharSequence,
    sizeSp: Int,
    bold: Boolean = false,
    serif: Boolean = false,
    color: Int = INK,
): TextView = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp.toFloat())
    setTextColor(color)
    typeface = Typeface.create(
        if (serif) Typeface.SERIF else Typeface.SANS_SERIF,
        if (bold) Typeface.BOLD else Typeface.NORMAL,
    )
    ellipsize = TextUtils.TruncateAt.END
}

/** A flat, ripple-free button: black-on-white outline, or white-on-black when [filled]. */
fun Context.flatButton(
    text: CharSequence,
    filled: Boolean = false,
    sizeSp: Int = 18,
    minHeightDp: Int = 64,
    onClick: () -> Unit,
): TextView = label(text, sizeSp, bold = true, color = if (filled) PAPER else INK).apply {
    gravity = Gravity.CENTER
    maxLines = 1
    minHeight = dp(minHeightDp)
    setPadding(dp(20), dp(8), dp(20), dp(8))
    background = box(strokeDp = 2, fill = if (filled) INK else PAPER)
    setOnClickListener { onClick() }
}

fun Context.rule(): View = View(this).apply { setBackgroundColor(INK) }

fun lp(w: Int, h: Int, weight: Float = 0f, top: Int = 0, left: Int = 0, bottom: Int = 0, right: Int = 0) =
    LinearLayout.LayoutParams(w, h, weight).apply { setMargins(left, top, right, bottom) }

const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

fun Context.isWatcherEnabled(): Boolean {
    val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?: return false
    val me = ComponentName(this, KindleWatcherService::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
}
