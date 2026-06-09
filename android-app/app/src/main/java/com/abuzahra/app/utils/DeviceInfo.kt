package com.abuzahra.app.utils

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import java.net.NetworkInterface
import java.util.Locale

object DeviceInfo {

    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString(Constants.KEY_DEVICE_ID, null)
        if (deviceId != null) return deviceId

        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
        )
        deviceId = "dev_${androidId}"
        prefs.edit().putString(Constants.KEY_DEVICE_ID, deviceId).apply()
        return deviceId
    }

    fun getDeviceModel(): String = Build.MODEL
    fun getDeviceBrand(): String = Build.BRAND
    fun getDeviceOS(): String = "Android ${Build.VERSION.RELEASE}"
    fun getDeviceSDK(): Int = Build.VERSION.SDK_INT
    fun getDeviceManufacturer(): String = Build.MANUFACTURER

    fun getSIMInfo(context: Context): Map<String, String> {
        val info = mutableMapOf<String, String>()
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            info["simState"] = (tm.simState).toString()
            info["simOperator"] = tm.simOperatorName ?: "Unknown"
            info["simCountry"] = tm.simCountryIso?.uppercase(Locale.getDefault()) ?: "Unknown"
            info["networkOperator"] = tm.networkOperatorName ?: "Unknown"
            info["phoneType"] = when (tm.phoneType) {
                TelephonyManager.PHONE_TYPE_GSM -> "GSM"
                TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
                TelephonyManager.PHONE_TYPE_SIP -> "SIP"
                else -> "Unknown"
            }
        } catch (e: Exception) {
            info["error"] = e.message ?: "Unknown error"
        }
        return info
    }

    fun getIPAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr.hostAddress.contains(".")) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return "0.0.0.0"
    }

    fun getFullDeviceInfo(context: Context): Map<String, Any> {
        return mapOf(
            "model" to getDeviceModel(),
            "brand" to getDeviceBrand(),
            "manufacturer" to getDeviceManufacturer(),
            "os" to getDeviceOS(),
            "sdk" to getDeviceSDK(),
            "deviceId" to getDeviceId(context),
            "ip" to getIPAddress(),
            "sim" to getSIMInfo(context)
        )
    }
}
