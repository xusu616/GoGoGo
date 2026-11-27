package com.suku616miao.meow.miaowuwu.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermanentCornersPreferenceTest {
    @Test fun permanentCornersAreOptInAndPersistTheirSwitch() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val testPreferences = base.getSharedPreferences("permanent_corners_regression", Context.MODE_PRIVATE)
        testPreferences.edit().clear().commit()
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = testPreferences
        }
        assertFalse(UiPreferences.permanentCorners(context))
        assertEquals(150f, UiPreferences.stiffness(context), 0f)
        assertEquals(0.6f, UiPreferences.damping(context), 0f)
        testPreferences.edit().putBoolean("permanent_corners", true).commit()
        assertTrue(UiPreferences.permanentCorners(context))
        testPreferences.edit().putBoolean("permanent_corners", false).commit()
        assertFalse(UiPreferences.permanentCorners(context))
        base.deleteSharedPreferences("permanent_corners_regression")
    }
}
