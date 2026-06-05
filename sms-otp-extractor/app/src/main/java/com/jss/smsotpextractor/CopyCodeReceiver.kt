package com.jss.smsotpextractor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CopyCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = intent.getStringExtra(EXTRA_CODE) ?: ResultStore.latestCode(context) ?: return
        OtpClipboard.copy(context, code)
    }

    companion object {
        const val EXTRA_CODE = "code"
    }
}
