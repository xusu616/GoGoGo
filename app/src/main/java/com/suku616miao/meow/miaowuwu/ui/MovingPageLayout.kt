package com.suku616miao.meow.miaowuwu.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.util.AttributeSet
import android.widget.FrameLayout

/** The clipping path travels with the animated page, including software-rendered exit snapshots. */
class MovingPageLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {
    private val clippingPath = Path()
    private var radii = FloatArray(8)

    fun setCornerRadii(cornerRadii: FloatArray) {
        require(cornerRadii.size == 8)
        radii = cornerRadii.copyOf()
        updateClippingPath()
        invalidate()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateClippingPath()
    }

    private fun updateClippingPath() {
        clippingPath.reset()
        clippingPath.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radii, Path.Direction.CW)
    }

    override fun draw(canvas: Canvas) {
        val saved = canvas.save()
        canvas.clipPath(clippingPath)
        super.draw(canvas)
        canvas.restoreToCount(saved)
    }
}
