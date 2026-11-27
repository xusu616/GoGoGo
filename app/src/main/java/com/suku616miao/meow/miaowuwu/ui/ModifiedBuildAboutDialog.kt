package com.suku616miao.meow.miaowuwu.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.suku616miao.meow.miaowuwu.BuildConfig
import com.suku616miao.meow.miaowuwu.R
import com.suku616miao.meow.miaowuwu.logging.AppLogger

object ModifiedBuildAboutDialog {
    @JvmStatic fun show(context: Context) {
        val message = SpannableStringBuilder(context.getString(R.string.modified_build_intro, BuildConfig.VERSION_NAME))
        val repositories = listOf(
            "GitHub" to "https://github.com/xusu616/GoGoGo",
            "GitLab" to "https://gitlab.com/xusu616/GoGoGo"
        )
        for ((label, url) in repositories) {
            message.append("\n\n$label\n")
            val start = message.length
            message.append(url)
            message.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (error: ActivityNotFoundException) {
                        AppLogger.w("GoRepositoryLink", "No browser available for repository link", error)
                        Toast.makeText(context, R.string.modified_build_no_browser, Toast.LENGTH_LONG).show()
                    }
                }
            }, start, message.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_App_RoundedInputDialog)
            .setTitle(R.string.modified_build_about)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.setOnShowListener {
            val links = requireNotNull(dialog.findViewById<TextView>(android.R.id.message))
            links.movementMethod = LinkMovementMethod.getInstance()
        }
        dialog.show()
    }
}
