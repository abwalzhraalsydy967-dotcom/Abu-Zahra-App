package com.abuzahra.app.handler

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

class SecurityHandler(private val context: Context) {

    fun hideApp(): Any {
        return try {
            val pm = context.packageManager
            val componentName = ComponentName(context, "com.abuzahra.app.MainActivity")
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            mapOf("status" to "success", "message" to "App icon hidden from launcher")
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun showApp(): Any {
        return try {
            val pm = context.packageManager
            val componentName = ComponentName(context, "com.abuzahra.app.MainActivity")
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            mapOf("status" to "success", "message" to "App icon restored in launcher")
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun setAntiUninstall(enable: Boolean): Any {
        return mapOf(
            "status" to "not_implemented",
            "message" to "Anti-uninstall requires Device Administrator API registration. ${if (enable) "Enable" else "Disable"} requested."
        )
    }
}
