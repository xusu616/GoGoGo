package com.suku616miao.meow.miaowuwu.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.suku616miao.meow.miaowuwu.MainActivity
import com.suku616miao.meow.miaowuwu.R

class ConsentFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(24.dp, 24.dp, 24.dp, 24.dp) }
        layout.addView(TextView(context).apply { setText(R.string.welcome_title); textSize = 26f })
        val scroll = ScrollView(context)
        scroll.addView(TextView(context).apply {
            text = getString(R.string.app_agreement_content) + "\n\n" + getString(R.string.app_privacy_content)
            setPadding(0, 16.dp, 0, 16.dp); textSize = 14f
        })
        layout.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(MaterialButton(context).apply {
            setText(R.string.welcome_continue)
            setOnClickListener {
                PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putBoolean("KEY_ACCEPT_AGREEMENT", true).putBoolean("KEY_ACCEPT_PRIVACY", true).apply()
                (requireActivity() as MainActivity).completeConsent()
            }
        })
        return layout
    }
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
