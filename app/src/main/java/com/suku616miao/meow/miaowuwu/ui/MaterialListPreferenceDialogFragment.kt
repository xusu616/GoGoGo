package com.suku616miao.meow.miaowuwu.ui

import android.app.Dialog
import android.os.Bundle
import androidx.preference.ListPreferenceDialogFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.suku616miao.meow.miaowuwu.R

/** AndroidX still owns restored selection and immediate persistence; only the dialog shell changes. */
class MaterialListPreferenceDialogFragment : ListPreferenceDialogFragmentCompat() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_RoundedInputDialog)
            .setTitle(preference.dialogTitle)
            .setIcon(preference.dialogIcon)
            .setPositiveButton(preference.positiveButtonText, this)
            .setNegativeButton(preference.negativeButtonText, this)
        onPrepareDialogBuilder(builder)
        return builder.create()
    }

    companion object {
        @JvmStatic fun newInstance(key: String): MaterialListPreferenceDialogFragment =
            MaterialListPreferenceDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_KEY, key) }
            }
    }
}
