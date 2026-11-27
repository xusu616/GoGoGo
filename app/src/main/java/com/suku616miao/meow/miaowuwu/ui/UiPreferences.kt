package com.suku616miao.meow.miaowuwu.ui

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.chip.Chip
import com.google.android.material.navigation.NavigationView
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.textfield.TextInputLayout
import com.suku616miao.meow.miaowuwu.R

object UiPreferences {
    const val ORIGINAL_COLOR = 0xFF008577.toInt()
    const val DEFAULT_SPRING_STIFFNESS = 150f
    const val DEFAULT_SPRING_DAMPING = 0.6f
    fun prefs(context: Context) = context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)
    fun backEnabled(context: Context) = prefs(context).getBoolean("predictive_back", true)
    fun backStrength(context: Context) = prefs(context).getFloat("back_strength", 0.6f)
    fun springEnabled(context: Context) = prefs(context).getBoolean("spring_enabled", true)
    fun permanentCorners(context: Context) = prefs(context).getBoolean("permanent_corners", false)
    fun stiffness(context: Context) = prefs(context).getFloat("spring_stiffness", DEFAULT_SPRING_STIFFNESS)
    fun damping(context: Context) = prefs(context).getFloat("spring_damping", DEFAULT_SPRING_DAMPING)
    fun seed(context: Context) = prefs(context).getInt("theme_color", ORIGINAL_COLOR)

    @JvmStatic fun applyTheme(context: android.app.Activity) {
        context.setTheme(R.style.AppTheme)
        if (seed(context) != ORIGINAL_COLOR) {
            DynamicColors.applyToActivityIfAvailable(context, DynamicColorsOptions.Builder()
                .setContentBasedSource(seed(context)).build())
        }
    }

    /** The role colors work on the Android 8 minimum too; dynamic resource overlays require Android 12. */
    @JvmStatic fun applyViewColors(root: View) {
        val custom = seed(root.context) != ORIGINAL_COLOR
        val palette = UiColors(root.context)
        applyViewColors(root, palette, custom)
    }

    private fun applyViewColors(view: View, palette: UiColors, custom: Boolean) {
        if (custom && view is TextView) view.setTextColor(palette.onSurface)
        when (view) {
            is Slider -> if (custom) {
                view.thumbTintList = enabledColors(palette.primary)
                view.trackActiveTintList = enabledColors(palette.primary)
                view.trackInactiveTintList = enabledColors(palette.outline)
                view.tickActiveTintList = enabledColors(palette.onPrimary)
                view.tickInactiveTintList = enabledColors(palette.onSurface)
            }
            is Chip -> if (custom) {
                view.chipBackgroundColor = selectionColors(palette.primaryContainer, palette.high)
                view.chipStrokeColor = selectionColors(palette.primary, palette.outline)
                view.setTextColor(selectionColors(palette.onPrimaryContainer, palette.onSurface))
            }
            is Toolbar -> {
                view.setBackgroundColor(palette.primary)
                view.setTitleTextColor(palette.onPrimary)
                view.setSubtitleTextColor(palette.onPrimary)
                view.navigationIcon?.mutate()?.setTint(palette.onPrimary)
                for (index in 0 until view.menu.size()) view.menu.getItem(index).icon?.mutate()?.setTint(palette.onPrimary)
            }
            is NavigationView -> if (custom) {
                (view.background as? MaterialShapeDrawable)?.fillColor = ColorStateList.valueOf(palette.high)
                view.itemIconTintList = selectionColors(palette.primary, palette.onSurface)
                view.itemTextColor = selectionColors(palette.primary, palette.onSurface)
            }
            is MaterialButton -> if (custom) {
                val outlined = view.strokeWidth > 0
                val transparent = Color.alpha(view.backgroundTintList?.defaultColor ?: Color.TRANSPARENT) == 0
                val textColor = if (outlined || transparent) palette.primary else palette.onPrimary
                view.setTextColor(enabledColors(textColor))
                view.iconTint = enabledColors(textColor)
                view.strokeColor = enabledColors(palette.outline)
                if (!outlined && !transparent) view.backgroundTintList = enabledColors(palette.primary)
                view.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.primary, 32))
            }
            is FloatingActionButton -> {
                val lightRoles = MaterialColors.getColorRoles(seed(view.context), true)
                view.backgroundTintList = ColorStateList.valueOf(lightRoles.accentContainer)
                view.imageTintList = ColorStateList.valueOf(lightRoles.onAccentContainer)
            }
            is MaterialCardView -> if (custom) {
                view.setCardBackgroundColor(palette.high)
                view.strokeColor = palette.outline
            }
            is TextInputLayout -> if (custom) {
                view.boxStrokeColor = palette.primary
                view.defaultHintTextColor = enabledColors(palette.onSurface)
                view.hintTextColor = ColorStateList.valueOf(palette.primary)
            }
            is MaterialSwitch -> if (custom) {
                view.thumbTintList = selectionColors(palette.onPrimary, palette.outline)
                view.trackTintList = selectionColors(palette.primary, palette.high)
                view.trackDecorationTintList = selectionColors(Color.TRANSPARENT, palette.outline)
            }
            is SwitchCompat -> if (custom) {
                view.thumbTintList = selectionColors(palette.primary, palette.outline)
                view.trackTintList = selectionColors(palette.primaryContainer, palette.high)
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) applyViewColors(view.getChildAt(index), palette, custom)
        }
        if (view is Toolbar && custom) tintHeaderText(view, palette.onPrimary)
        if (view is RecyclerView && custom && view.getTag(R.id.ui_color_binding) == null) {
            // Preference rows are bound after the Fragment root is created and recycled while scrolling.
            val listener = object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(child: View) { applyViewColors(child, palette, true) }
                override fun onChildViewDetachedFromWindow(child: View) = Unit
            }
            view.setTag(R.id.ui_color_binding, listener)
            view.addOnChildAttachStateChangeListener(listener)
        }
        // The drawer header sits on a colored surface, not the page surface.
        if (view is NavigationView && custom && view.headerCount > 0) {
            val header = view.getHeaderView(0)
            header.setBackgroundColor(palette.primary)
            tintHeaderText(header, palette.onPrimary)
        }
    }

    private fun tintHeaderText(view: View, color: Int) {
        if (view is TextView) view.setTextColor(color)
        if (view is ViewGroup) for (index in 0 until view.childCount) tintHeaderText(view.getChildAt(index), color)
    }

    private fun enabledColors(color: Int) = ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
        intArrayOf(ColorUtils.setAlphaComponent(color, 97), color)
    )

    private fun selectionColors(selected: Int, normal: Int) = ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(ColorUtils.setAlphaComponent(normal, 97), selected, normal)
    )
}

class UiColors(private val context: Context) {
    private fun color(attr: Int) = MaterialColors.getColor(context, attr, javaClass.simpleName)
    private val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private val custom = UiPreferences.seed(context) != UiPreferences.ORIGINAL_COLOR
    private val roles = MaterialColors.getColorRoles(UiPreferences.seed(context), !dark)
    val primary = if (custom) roles.accent else color(androidx.appcompat.R.attr.colorPrimary)
    val onPrimary = if (custom) roles.onAccent else color(com.google.android.material.R.attr.colorOnPrimary)
    val primaryContainer = if (custom) roles.accentContainer else color(com.google.android.material.R.attr.colorPrimaryContainer)
    val onPrimaryContainer = if (custom) roles.onAccentContainer else color(com.google.android.material.R.attr.colorOnPrimaryContainer)
    val onSurface = color(com.google.android.material.R.attr.colorOnSurface)
    val high = if (custom) ColorUtils.blendARGB(color(com.google.android.material.R.attr.colorSurfaceContainerHigh), primary, 0.08f)
        else color(com.google.android.material.R.attr.colorSurfaceContainerHigh)
    val outline = color(com.google.android.material.R.attr.colorOutline)
}
