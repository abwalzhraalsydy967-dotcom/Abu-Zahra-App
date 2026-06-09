package com.abuzahra.app.handler

import android.content.Context
import android.content.pm.PackageManager

class SocialHandler(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    private fun getSocialAppInfo(packageName: String): Map<String, Any> {
        return try {
            val appInfo = pm.getPackageInfo(packageName, 0)
            mapOf(
                "installed" to true,
                "packageName" to packageName,
                "appName" to (appInfo.applicationInfo?.loadLabel(pm)?.toString() ?: packageName),
                "version" to appInfo.versionName
            )
        } catch (e: PackageManager.NameNotFoundException) {
            mapOf("installed" to false, "packageName" to packageName, "message" to "App not installed")
        }
    }

    fun getWhatsAppData(): Any {
        val info = getSocialAppInfo("com.whatsapp")
        return if (info["installed"] == true) {
            mapOf("status" to "success", "app" to info, "message" to "WhatsApp is installed. Direct database access requires root.")
        } else {
            mapOf("status" to "not_installed", "app" to info)
        }
    }

    fun getTelegramData(): Any {
        val info = getSocialAppInfo("org.telegram.messenger")
        return if (info["installed"] == true) {
            mapOf("status" to "success", "app" to info, "message" to "Telegram is installed.")
        } else {
            mapOf("status" to "not_installed", "app" to info)
        }
    }

    fun getInstagramData(): Any {
        val info = getSocialAppInfo("com.instagram.android")
        return if (info["installed"] == true) {
            mapOf("status" to "success", "app" to info, "message" to "Instagram is installed.")
        } else {
            mapOf("status" to "not_installed", "app" to info)
        }
    }

    fun getMessengerData(): Any {
        val info = getSocialAppInfo("com.facebook.orca")
        return if (info["installed"] == true) {
            mapOf("status" to "success", "app" to info, "message" to "Messenger is installed.")
        } else {
            mapOf("status" to "not_installed", "app" to info)
        }
    }

    fun getSnapchatData(): Any {
        val info = getSocialAppInfo("com.snapchat.android")
        return if (info["installed"] == true) {
            mapOf("status" to "success", "app" to info)
        } else {
            mapOf("status" to "not_installed", "app" to info)
        }
    }

    fun getTiktokData(): Any {
        val info = getSocialAppInfo("com.zhiliaoapp.musically")
            .let { if (it["installed"] != true) getSocialAppInfo("com.ss.android.ugc.trill") else it }
        return mapOf("status" to "success", "app" to info)
    }

    fun getTwitterData(): Any {
        val info = getSocialAppInfo("com.twitter.android")
        return mapOf("status" to "success", "app" to info)
    }

    fun getViberData(): Any {
        val info = getSocialAppInfo("com.viber.voip")
        return mapOf("status" to "success", "app" to info)
    }

    fun getSignalData(): Any {
        val info = getSocialAppInfo("org.thoughtcrime.securesms")
        return mapOf("status" to "success", "app" to info)
    }

    fun getFacebookData(): Any {
        val info = getSocialAppInfo("com.facebook.katana")
        return mapOf("status" to "success", "app" to info)
    }
}
