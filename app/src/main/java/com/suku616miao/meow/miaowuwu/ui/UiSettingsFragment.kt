package com.suku616miao.meow.miaowuwu.ui

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.suku616miao.meow.miaowuwu.R

class UiSettingsFragment : PreferenceFragmentCompat() {
    private var pendingColor: ThemeColor? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "ui_settings"
        val context = requireContext()
        val prefs = UiPreferences.prefs(context)
        pendingColor = if (savedInstanceState?.containsKey("theme_pending_hue") == true) {
            ThemeColor(savedInstanceState.getFloat("theme_pending_hue"),
                savedInstanceState.getFloat("theme_pending_whiteness"), savedInstanceState.getFloat("theme_pending_value"))
        } else ThemeColor.fromArgb(UiPreferences.seed(context))
        preferenceScreen = preferenceManager.createPreferenceScreen(context)
        val motion = PreferenceCategory(context).apply { setTitle(R.string.ui_motion); isIconSpaceReserved = false }
        preferenceScreen.addPreference(motion)
        motion.addPreference(MaterialSwitchPreference(context).apply {
            key = "permanent_corners"
            setTitle(R.string.ui_permanent_corners)
            summary = context.getText(R.string.ui_permanent_corners_summary)
            setDefaultValue(false)
            isIconSpaceReserved = false
        })
        motion.addPreference(MaterialSwitchPreference(context).apply {
            key = "predictive_back"
            setTitle(R.string.ui_predictive)
            setSummary(R.string.ui_predictive_summary)
            setDefaultValue(true)
            isIconSpaceReserved = false
        })
        slider(motion, "back_strength", R.string.ui_back_strength, 0.6f, 0f, 1f, 0.05f, "%.2f")
        motion.addPreference(MaterialSwitchPreference(context).apply {
            key = "spring_enabled"
            setTitle(R.string.ui_spring)
            setDefaultValue(true)
            isIconSpaceReserved = false
        })
        slider(motion, "spring_stiffness", R.string.ui_stiffness, UiPreferences.DEFAULT_SPRING_STIFFNESS,
            100f, 1500f, 50f, "%.0f", R.string.ui_stiffness_summary)
        slider(motion, "spring_damping", R.string.ui_damping, UiPreferences.DEFAULT_SPRING_DAMPING,
            0.3f, 1.5f, 0.1f, "%.1f", R.string.ui_damping_summary)
        val colors = PreferenceCategory(context).apply { setTitle(R.string.ui_colors); isIconSpaceReserved = false }
        preferenceScreen.addPreference(colors)
        val colorRing = InlineColorRingPreference(context, checkNotNull(pendingColor),
            onPreview = { pendingColor = it }, onApply = { pendingColor = it; applyColor(it.argb) })
        colors.addPreference(InlineColorPresetsPreference(context) {
            val selection = ThemeColor.fromArgb(it)
            pendingColor = selection
            colorRing.setSelection(selection)
            applyColor(it)
        })
        colors.addPreference(colorRing)
        preferenceScreen.addPreference(Preference(context).apply {
            setTitle(R.string.ui_reset)
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                MaterialAlertDialogBuilder(context).setTitle(R.string.ui_reset)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        pendingColor = ThemeColor.fromArgb(UiPreferences.ORIGINAL_COLOR)
                        prefs.edit().clear().apply()
                        requireActivity().recreate()
                    }
                    .setNegativeButton(android.R.string.cancel, null).show()
                true
            }
        })
    }

    private fun slider(parent: PreferenceCategory, keyName: String, title: Int, initial: Float,
        minimum: Float, maximum: Float, step: Float, format: String, detail: Int? = null) {
        parent.addPreference(InlineSliderPreference(requireContext(), keyName, title, initial, minimum, maximum, step, format, detail))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingColor?.let {
            outState.putFloat("theme_pending_hue", it.hue)
            outState.putFloat("theme_pending_whiteness", it.whiteness)
            outState.putFloat("theme_pending_value", it.baseValue)
        }
    }

    private fun applyColor(seed: Int) {
        if (UiPreferences.seed(requireContext()) == seed) return
        UiPreferences.prefs(requireContext()).edit().putInt("theme_color", seed).apply()
        requireActivity().recreate()
    }

}
