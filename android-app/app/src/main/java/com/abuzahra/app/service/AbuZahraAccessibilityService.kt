package com.abuzahra.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AbuZahraAccessibilityService : AccessibilityService() {

    private val TAG = "AbuZahraAccessibility"

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED

            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }

        serviceInfo = info
        Log.d(TAG, "Accessibility service configured")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    val packageName = event.packageName?.toString() ?: return
                    val className = event.className?.toString() ?: ""
                    Log.d(TAG, "App opened: $packageName | $className")
                }
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                    val packageName = event.packageName?.toString() ?: return
                    val text = event.text?.joinToString(" ") ?: ""
                    Log.d(TAG, "Notification: $packageName | $text")
                }
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    val text = event.text?.joinToString(" ") ?: ""
                    if (text.isNotEmpty()) {
                        Log.d(TAG, "Text changed: $text")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing event", e)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Accessibility service destroyed")
    }
}
