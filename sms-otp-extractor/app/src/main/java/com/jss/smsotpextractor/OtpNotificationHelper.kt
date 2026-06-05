package com.jss.smsotpextractor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.jss.smsotpextractor.otp.OtpDecision

object OtpNotificationHelper {
    private const val CHANNEL_ID = "otp_detected"
    private const val NOTIFICATION_ID = 483_920

    fun showIfDetected(context: Context, decision: OtpDecision) {
        val detected = decision as? OtpDecision.OtpDetected ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        ensureChannel(manager)

        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val copyIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, CopyCodeReceiver::class.java).putExtra(CopyCodeReceiver.EXTRA_CODE, detected.code),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("OTP detected")
            .setContentText(detected.code)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Code ${detected.code} selected by ${detected.source}"))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_save, "Copy", copyIntent)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "OTP detections",
            NotificationManager.IMPORTANCE_HIGH,
        )
        manager.createNotificationChannel(channel)
    }
}
