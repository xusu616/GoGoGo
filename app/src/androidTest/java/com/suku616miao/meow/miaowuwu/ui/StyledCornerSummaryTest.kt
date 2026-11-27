package com.suku616miao.meow.miaowuwu.ui

import android.text.Spanned
import android.text.style.StrikethroughSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.suku616miao.meow.miaowuwu.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StyledCornerSummaryTest {
    @Test fun resourceRetainsStrikethroughForTrailingSentence() {
        val text = InstrumentationRegistry.getInstrumentation().targetContext.getText(R.string.ui_permanent_corners_summary)
        assertTrue(text is Spanned)
        val styled = text as Spanned
        val spans = styled.getSpans(0, text.length, StrikethroughSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals("…我在十天前还不知道这个词的网络含义",
            text.subSequence(styled.getSpanStart(spans[0]), styled.getSpanEnd(spans[0])).toString())
    }
}
