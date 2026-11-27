package com.suku616miao.meow.miaowuwu.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.suku616miao.meow.miaowuwu.R
import kotlin.math.*

// Adapted from the user's Free-Smart-lamp ColorRingView; preserves release-to-commit and accessibility.
enum class RingKind { HUE, WHITENESS }

/** Separate accessible range controls share a color preview and commit only on release. */
@SuppressLint("ViewConstructor") // Constructed only by LampScreen, never inflated from XML.
class ColorRingView(
    context: Context,
    initial: ThemeColor,
    private val kind: RingKind,
    private val colors: UiColors,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val arcBounds = RectF()
    private var gradient: SweepGradient? = null
    private var gestureStart = initial
    var onPreview: (ThemeColor) -> Unit = {}
    var onColorSelected: (ThemeColor) -> Unit = {}
    var blockedBySystemColors = false
    var onBlockedInteraction: () -> Unit = {}
    var selection = initial
        set(value) {
            val hueChanged = field.hue != value.hue || field.baseValue != value.baseValue
            field = value
            if (hueChanged && kind == RingKind.WHITENESS) updateGradient()
            updateDescription()
            invalidate()
        }
    private val maximum get() = if (kind == RingKind.HUE) 360f else 100f
    private val value get() = if (kind == RingKind.HUE) selection.hue else selection.whiteness * 100f
    private val radius get() = min(width, height) / 2f - 12 * density

    init { isFocusable = true; isClickable = true; updateDescription() }

    private fun updateDescription() {
        contentDescription = context.getString(
            if (kind == RingKind.HUE) R.string.theme_hue else R.string.theme_whiteness,
            value.roundToInt(),
        )
    }

    private fun updateGradient() {
        val x = width / 2f
        val y = height / 2f
        gradient = if (kind == RingKind.HUE) {
            SweepGradient(x, y, intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED), null)
        } else {
            SweepGradient(x, y, intArrayOf(selection.copy(whiteness = 0f).argb, Color.WHITE, Color.WHITE), floatArrayOf(0f, 300f / 360f, 1f)).apply {
                setLocalMatrix(Matrix().apply { setRotate(120f, x, y) })
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { updateGradient() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val x = width / 2f
        val y = height / 2f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 16 * density
        paint.strokeCap = Paint.Cap.ROUND
        paint.shader = gradient
        if (kind == RingKind.HUE) canvas.drawCircle(x, y, radius, paint)
        else {
            paint.strokeCap = Paint.Cap.BUTT
            arcBounds.set(x - radius, y - radius, x + radius, y + radius)
            canvas.drawArc(arcBounds, 120f, 300f, false, paint)
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.color = selection.copy(whiteness = 0f).argb
            canvas.drawCircle(x - radius * 0.5f, y + radius * 0.8660254f, 8 * density, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(x + radius * 0.5f, y + radius * 0.8660254f, 8 * density, paint)
        }
        paint.shader = null
        paint.style = Paint.Style.FILL
        if (kind == RingKind.HUE) {
            paint.color = colors.high
            canvas.drawCircle(x, y, radius - 18 * density, paint)
            paint.color = selection.argb
            canvas.drawCircle(x, y, radius - 23 * density, paint)
        }
        val angle = Math.toRadians(if (kind == RingKind.HUE) selection.hue.toDouble() else 120.0 + selection.whiteness * 300.0)
        val px = x + radius * cos(angle).toFloat()
        val py = y + radius * sin(angle).toFloat()
        // A dark outline keeps both handles visible against pale colors and white.
        paint.color = colors.outline
        canvas.drawCircle(px, py, 11 * density, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(px, py, 9 * density, paint)
        paint.color = if (kind == RingKind.HUE) selection.copy(whiteness = 0f).argb else selection.argb
        canvas.drawCircle(px, py, 6 * density, paint)
        if (isFocused) {
            paint.style = Paint.Style.STROKE
            paint.color = colors.primary
            paint.strokeWidth = 2 * density
            canvas.drawCircle(x, y, min(width, height) / 2f - density, paint)
        }
    }

    private fun angle(x: Float, y: Float) = ((Math.toDegrees(atan2((y - height / 2f).toDouble(), (x - width / 2f).toDouble())) + 360) % 360).toFloat()

    private fun pick(x: Float, y: Float) {
        val hue = angle(x, y)
        val next = if (kind == RingKind.HUE) selection.copy(hue = hue) else {
            val offset = (hue - 120f + 360f) % 360f
            val clamped = when {
                offset <= 300f -> offset
                offset < 330f -> 300f
                else -> 0f
            }
            selection.copy(whiteness = clamped / 300f)
        }
        selection = next
        onPreview(next)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled && !blockedBySystemColors) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val distance = hypot(event.x - width / 2f, event.y - height / 2f)
                if (distance < radius - 13 * density || distance > radius + 12 * density) return false
                if (kind == RingKind.WHITENESS) {
                    val offset = (angle(event.x, event.y) - 120f + 360f) % 360f
                    // Include the rounded end caps so 0% and 100% are easy to touch.
                    if (offset > 315f && offset < 345f) return false
                }
                gestureStart = selection
                parent.requestDisallowInterceptTouchEvent(true)
                if (blockedBySystemColors) onBlockedInteraction()
                else { isPressed = true; pick(event.x, event.y) }
            }
            MotionEvent.ACTION_MOVE -> if (!blockedBySystemColors) pick(event.x, event.y)
            MotionEvent.ACTION_UP -> {
                isPressed = false
                parent.requestDisallowInterceptTouchEvent(false)
                if (!blockedBySystemColors) { pick(event.x, event.y); performClick() }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (!blockedBySystemColors) { selection = gestureStart; onPreview(gestureStart) }
                isPressed = false
                parent.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (blockedBySystemColors) onBlockedInteraction()
        else if (isEnabled) onColorSelected(selection)
        return true
    }

    private fun setValue(newValue: Float) {
        selection = if (kind == RingKind.HUE) selection.copy(hue = (newValue % 360f + 360f) % 360f)
        else selection.copy(whiteness = newValue.coerceIn(0f, 100f) / 100f)
        onPreview(selection)
        onColorSelected(selection)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isEnabled && keyCode in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)) {
            val direction = if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) -1 else 1
            setValue(value + direction * if (kind == RingKind.HUE) 15f else 5f)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Suppress("DEPRECATION") // RangeInfo.obtain supports the app's Android 8 minimum.
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.SeekBar"
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, 0f, maximum, value)
        if (isEnabled) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (!isEnabled) return super.performAccessibilityAction(action, arguments)
        val step = if (kind == RingKind.HUE) 15f else 5f
        val target = when (action) {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id -> arguments?.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)?.coerceIn(0f, maximum) ?: return false
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> value + step
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> value - step
            else -> return super.performAccessibilityAction(action, arguments)
        }
        if (!target.isFinite()) return false
        setValue(target)
        return true
    }
}

