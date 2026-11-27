package com.suku616miao.meow.miaowuwu.ui

import android.content.Context
import android.util.AttributeSet
import androidx.preference.SwitchPreferenceCompat
import com.suku616miao.meow.miaowuwu.R

/** Keep Preference persistence and row interaction while rendering the actual Material 3 widget. */
class MaterialSwitchPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    SwitchPreferenceCompat(context, attrs) {
    init {
        widgetLayoutResource = R.layout.preference_widget_material_switch
    }
}
