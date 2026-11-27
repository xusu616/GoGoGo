package com.suku616miao.meow.miaowuwu.ui

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.preference.PreferenceViewHolder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.materialswitch.MaterialSwitch
import com.suku616miao.meow.miaowuwu.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaterialSwitchPreferenceTest {
    @Test fun rowClickBindsCheckedStateToActualMaterialSwitch() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.AppTheme)
            val preference = MaterialSwitchPreference(context).apply { isPersistent = false }
            val switchView = LayoutInflater.from(context).inflate(preference.widgetLayoutResource, null)
            assertTrue(switchView is MaterialSwitch)
            val row = FrameLayout(context).apply { addView(switchView) }
            val holder = PreferenceViewHolder.createInstanceForTests(row)
            preference.onBindViewHolder(holder)
            assertFalse((switchView as MaterialSwitch).isChecked)
            preference.performClick()
            preference.onBindViewHolder(holder)
            assertTrue(preference.isChecked)
            assertTrue(switchView.isChecked)
            preference.performClick()
            preference.onBindViewHolder(holder)
            assertFalse(preference.isChecked)
            assertFalse(switchView.isChecked)
        }
    }
}
