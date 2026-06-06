package com.jss.smsotpextractor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.jss.smsotpextractor.otp.OtpDecision

object OtpClipboard {
    fun copy(context: Context, code: String) {
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("OTP", code))
    }

    fun copyIfDetected(context: Context, decision: OtpDecision): Boolean {
        val detected = decision as? OtpDecision.OtpDetected ?: return false
        copy(context, detected.code)
        return true
    }
}
