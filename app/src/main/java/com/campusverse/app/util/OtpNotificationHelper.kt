package com.campusverse.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.campusverse.app.R

object OtpNotificationHelper {
    private const val CHANNEL_ID = "campusverse_auth_channel"
    private const val CHANNEL_NAME = "CampusVerse Security & Auth"
    private const val NOTIFICATION_ID = 1001

    fun showOtpNotification(context: Context, otpCode: String, recipientEmail: String) {
        createNotificationChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("CampusVerse Verification Code")
            .setContentText("Your security code is $otpCode (Valid for 10 min)")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Your CampusVerse verification code for $recipientEmail is: $otpCode\n\nEnter this 6-digit code in the app to complete verification.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        try {
            val manager = NotificationManagerCompat.from(context)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission not yet granted on Android 13+
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "CampusVerse Authentication and Verification Alerts"
                enableLights(true)
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }
}
