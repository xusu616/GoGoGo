package com.suku616miao.meow.miaowuwu.ui

import android.graphics.Color
import android.widget.FrameLayout
import androidx.core.view.drawToBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MovingPageLayoutTest {
    @Test fun clippingIncludesBackgroundAndChildAtEachScreenCorner() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val page = MovingPageLayout(instrumentation.targetContext)
            page.setBackgroundColor(Color.BLACK)
            page.addView(android.view.View(page.context).apply { setBackgroundColor(Color.RED) },
                FrameLayout.LayoutParams(-1, -1))
            page.setCornerRadii(floatArrayOf(20f, 20f, 0f, 0f, 30f, 30f, 0f, 0f))
            page.measure(android.view.View.MeasureSpec.makeMeasureSpec(100, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(100, android.view.View.MeasureSpec.EXACTLY))
            page.layout(0, 0, 100, 100)
            val bitmap = page.drawToBitmap()
            assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
            assertEquals(Color.RED, bitmap.getPixel(99, 0))
            assertEquals(0, Color.alpha(bitmap.getPixel(99, 99)))
            assertEquals(Color.RED, bitmap.getPixel(0, 99))
            assertEquals(Color.RED, bitmap.getPixel(50, 50))
            bitmap.recycle()
        }
    }

    @Test fun roundedEdgeMovesWithAnimatedPageInsteadOfFixedParent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val parent = FrameLayout(instrumentation.targetContext).apply { setBackgroundColor(Color.GREEN) }
            val page = MovingPageLayout(parent.context).apply {
                setBackgroundColor(Color.RED)
                setCornerRadii(FloatArray(8) { 20f })
            }
            parent.addView(page, FrameLayout.LayoutParams(100, 100))
            parent.measure(android.view.View.MeasureSpec.makeMeasureSpec(160, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(160, android.view.View.MeasureSpec.EXACTLY))
            parent.layout(0, 0, 160, 160)
            page.translationX = 30f
            page.translationY = 40f
            val bitmap = parent.drawToBitmap()
            assertEquals(Color.GREEN, bitmap.getPixel(30, 40))
            assertEquals(Color.RED, bitmap.getPixel(80, 90))
            assertEquals(Color.GREEN, bitmap.getPixel(0, 0))
            bitmap.recycle()
        }
    }
}
