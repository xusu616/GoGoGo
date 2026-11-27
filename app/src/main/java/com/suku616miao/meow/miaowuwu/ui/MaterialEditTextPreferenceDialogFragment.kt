package com.suku616miao.meow.miaowuwu.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnPreDraw
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.suku616miao.meow.miaowuwu.R

/** Preserve AndroidX preference state, input binding and save validation with a Material input. */
class MaterialEditTextPreferenceDialogFragment : EditTextPreferenceDialogFragmentCompat() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val inputView = LayoutInflater.from(context).inflate(R.layout.preference_dialog_rounded_edittext, null)
        inputView.findViewById<TextInputLayout>(R.id.preference_rounded_input).hint = preference.dialogTitle
        onBindDialogView(inputView)
        val builder = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_App_RoundedInputDialog)
            .setTitle(preference.dialogTitle)
            .setIcon(preference.dialogIcon)
            .setPositiveButton(preference.positiveButtonText, this)
            .setNegativeButton(preference.negativeButtonText, this)
            .setView(inputView)
        onPrepareDialogBuilder(builder)
        return builder.create()
    }

    override fun onStart() {
        super.onStart()
        val input = requireDialog().findViewById<android.widget.EditText>(android.R.id.edit)
        val window = requireNotNull(requireDialog().window)
        input.requestFocus()
        input.doOnPreDraw { WindowInsetsControllerCompat(window, it).show(WindowInsetsCompat.Type.ime()) }
    }

    companion object {
        @JvmStatic fun newInstance(key: String): MaterialEditTextPreferenceDialogFragment =
            MaterialEditTextPreferenceDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_KEY, key) }
            }
    }
}
