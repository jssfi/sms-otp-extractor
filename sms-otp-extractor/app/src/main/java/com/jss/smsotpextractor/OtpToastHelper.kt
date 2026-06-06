package com.jss.smsotpextractor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

object OtpToastHelper {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun showCodeCopied(context: Context) {
        val appContext = context.applicationContext
        mainHandler.post {
            Toast.makeText(appContext, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }
}
