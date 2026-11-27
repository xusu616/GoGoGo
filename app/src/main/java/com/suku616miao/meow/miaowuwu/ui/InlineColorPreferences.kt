package com.suku616miao.meow.miaowuwu.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.suku616miao.meow.miaowuwu.R
import java.util.Locale

/** Presets stay in the settings page and retain the original application color. */
class InlineColorPresetsPreference(context: Context, private val onSelect: (Int) -> Unit) : Preference(context) {
    init {
        key = "theme_presets_inline"
        setTitle(R.string.ui_presets)
        layoutResource = R.layout.preference_inline_color_presets
        isSelectable = false
        isPersistent = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val chips = holder.findViewById(R.id.ui_theme_presets) as ChipGroup
        chips.removeAllViews()
        val current = UiPreferences.seed(context)
        val presets = listOf(
            R.string.ui_original to UiPreferences.ORIGINAL_COLOR,
            R.string.ui_theme_blue to 0xFF4285F4.toInt(),
            R.string.ui_theme_purple to 0xFF8B72CE.toInt(),
            R.string.ui_theme_rose to 0xFFC76C8C.toInt(),
            R.string.ui_theme_amber to 0xFFD89532.toInt()
        )
        for ((label, color) in presets) {
            val chip = Chip(context).apply {
                id = View.generateViewId()
                setText(label)
                isCheckable = true
                isCheckedIconVisible = false
                chipIcon = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setSize(18.dp, 18.dp)
                    setColor(color)
                }
                chipIconTint = ColorStateList.valueOf(color)
                isChipIconVisible = true
                setOnClickListener { onSelect(color) }
            }
            chips.addView(chip)
            chip.isChecked = color == current
        }
        UiPreferences.applyViewColors(holder.itemView)
    }

    private val Int.dp: Int get() = (this * context.resources.displayMetrics.density).toInt()
}

/** Only the pending color is retained; each binding owns its own temporary views and listeners. */
class InlineColorRingPreference(
    context: Context,
    initial: ThemeColor,
    private val onPreview: (ThemeColor) -> Unit,
    private val onApply: (ThemeColor) -> Unit
) : Preference(context) {
    private var selected = initial

    init {
        key = "theme_color_ring_inline"
        setTitle(R.string.ui_custom_color)
        layoutResource = R.layout.preference_inline_color_ring
        isSelectable = false
        isPersistent = false
    }

    fun setSelection(color: ThemeColor) {
        selected = color
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val hueHost = holder.findViewById(R.id.ui_theme_hue_host) as FrameLayout
        val whiteHost = holder.findViewById(R.id.ui_theme_whiteness_host) as FrameLayout
        hueHost.removeAllViews()
        whiteHost.removeAllViews()
        val colors = UiColors(context)
        val hue = ColorRingView(context, selected, RingKind.HUE, colors).apply { id = R.id.ui_theme_hue }
        val whiteness = ColorRingView(context, selected, RingKind.WHITENESS, colors).apply { id = R.id.ui_theme_whiteness }
        hueHost.addView(hue, FrameLayout.LayoutParams(-1, -1))
        whiteHost.addView(whiteness, FrameLayout.LayoutParams(-1, -1))
        val swatch = holder.findViewById(R.id.ui_theme_swatch)
        val hex = holder.findViewById(R.id.ui_theme_hex) as TextView
        val apply = holder.findViewById(R.id.ui_theme_apply) as MaterialButton
        val swatchBackground = GradientDrawable().apply { shape = GradientDrawable.OVAL }
        swatch.background = swatchBackground

        fun update(value: ThemeColor) {
            selected = value
            hue.selection = value
            whiteness.selection = value
            swatchBackground.setColor(value.argb)
            hex.text = String.format(Locale.ROOT, "#%06X", value.argb and 0xFFFFFF)
            apply.isEnabled = value.argb != UiPreferences.seed(context)
            onPreview(value)
        }

        hue.onPreview = ::update
        whiteness.onPreview = ::update
        apply.setOnClickListener { onApply(selected) }
        UiPreferences.applyViewColors(holder.itemView)
        update(selected)
    }
}
