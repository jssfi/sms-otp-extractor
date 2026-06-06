package com.jss.smsotpextractor

import android.content.Context
import com.jss.smsotpextractor.otp.AiOtpSelector

object OtpAiSelectorFactory {
    fun create(context: Context): AiOtpSelector = LiteRtAiOtpSelector(context)
}
