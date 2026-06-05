package com.abuzahra.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.abuzahra.app.service.FirebaseListenerService
import com.abuzahra.app.utils.Constants
import com.abuzahra.app.utils.DeviceInfo
import com.abuzahra.app.utils.NotificationHelper
import com.abuzahra.app.utils.PermissionHelper

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvDeviceId: TextView
    private lateinit var btnStart: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tv_status)
        tvDeviceId = findViewById(R.id.tv_device_id)
        btnStart = findViewById(R.id.btn_start)

        val deviceId = DeviceInfo.getDeviceId(this)
        tvDeviceId.text = "Device ID: $deviceId"

        if (isServiceRunning()) {
            tvStatus.text = "Status: Running"
            btnStart.text = "Stop Service"
        } else {
            tvStatus.text = "Status: Stopped"
        }

        btnStart.setOnClickListener {
            if (isServiceRunning()) {
                stopService()
            } else {
                requestPermissionsAndStart()
            }
        }

        requestPermissionsIfNeeded()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startService()
        } else {
            val denied = permissions.filter { !it.value }.keys
            Toast.makeText(this, "Missing permissions: $denied", Toast.LENGTH_LONG).show()
            startService() // Start anyway with available permissions
        }
    }

    private fun requestPermissionsIfNeeded() {
        val missing = PermissionHelper.getMissingPermissions(this)
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun requestPermissionsAndStart() {
        val missing = PermissionHelper.getMissingPermissions(this)
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            startService()
        }
    }

    private fun startService() {
        NotificationHelper.createNotificationChannel(this)
        val intent = Intent(this, FirebaseListenerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        tvStatus.text = "Status: Running"
        btnStart.text = "Stop Service"
        Toast.makeText(this, "Service started", Toast.LENGTH_SHORT).show()
    }

    private fun stopService() {
        val intent = Intent(this, FirebaseListenerService::class.java)
        stopService(intent)
        tvStatus.text = "Status: Stopped"
        btnStart.text = "Start Service"
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show()
    }

    private fun isServiceRunning(): Boolean {
        return DeviceInfo.getDeviceId(this).isNotEmpty()
    }
}
