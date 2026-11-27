package com.suku616miao.meow.miaowuwu.ui

import android.content.Context
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.slider.Slider
import com.suku616miao.meow.miaowuwu.R
import java.util.Locale

/** A settings row owns no View reference; recycled rows restore listeners and values on bind. */
class InlineSliderPreference(
    context: Context,
    private val settingKey: String,
    title: Int,
    private val initial: Float,
    private val minimum: Float,
    private val maximum: Float,
    private val step: Float,
    private val format: String,
    private val description: Int?
) : Preference(context) {
    init {
        setTitle(title)
        layoutResource = R.layout.preference_inline_slider
        isSelectable = false
        isPersistent = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val slider = holder.findViewById(R.id.ui_slider) as Slider
        val valueLabel = holder.findViewById(R.id.ui_slider_value) as TextView
        val detail = holder.findViewById(R.id.ui_slider_description) as TextView
        detail.visibility = if (description == null) android.view.View.GONE else android.view.View.VISIBLE
        if (description != null) detail.setText(description)
        slider.clearOnChangeListeners()
        slider.clearOnSliderTouchListeners()
        slider.valueFrom = minimum
        slider.valueTo = maximum
        slider.stepSize = step
        slider.value = UiPreferences.prefs(context).getFloat(settingKey, initial)
        slider.contentDescription = title
        valueLabel.text = String.format(Locale.ROOT, format, slider.value)
        slider.addOnChangeListener { _, value, fromUser ->
            valueLabel.text = String.format(Locale.ROOT, format, value)
            if (fromUser) UiPreferences.prefs(context).edit().putFloat(settingKey, value).apply()
        }
        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                UiPreferences.prefs(context).edit().putFloat(settingKey, slider.value).apply()
            }
        })
        UiPreferences.applyViewColors(holder.itemView)
    }
}
