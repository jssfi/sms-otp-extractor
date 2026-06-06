package com.jss.smsotpextractor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.jss.smsotpextractor.otp.OtpProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val smsText = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                    .joinToString(separator = "") { it.messageBody.orEmpty() }
                if (smsText.isNotBlank()) {
                    val processor = OtpProcessor(OtpAiSelectorFactory.create(context))
                    val decision = processor.process(smsText)
                    ResultStore.save(context, smsText, decision)
                    if (OtpClipboard.copyIfDetected(context, decision)) {
                        OtpToastHelper.showCodeCopied(context)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
