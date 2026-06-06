package com.abuzahra.app.handler

import android.Manifest
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Vibrator
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.view.WindowManager
import android.widget.Toast
import java.io.File

class ControlHandler(private val context: Context) {

    fun ping(): Any {
        return mapOf("status" to "success", "pong" to System.currentTimeMillis(), "device" to "Abu-Zahra-App")
    }

    fun vibrate(params: Map<*, *>): Any {
        return try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val duration = ((params["duration"] as? Number)?.toLong() ?: 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
            mapOf("status" to "success", "duration" to duration)
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun ring(): Any {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_RING, am.getStreamMaxVolume(AudioManager.STREAM_RING), 0)
            val uri = Settings.System.DEFAULT_RINGTONE_URI
            val intent = Intent(Intent.ACTION_VIEW).setData(uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            mapOf("status" to "success", "message" to "Ringtone playing")
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun screenshot(): Any {
        return mapOf("status" to "not_implemented", "message" to "Screenshot requires MediaProjection API")
    }

    fun takePhoto(camera: String): Any {
        return mapOf("status" to "not_implemented", "message" to "Camera capture requires CameraX: $camera")
    }

    fun recordAudio(params: Map<*, *>): Any {
        val duration = ((params["duration"] as? Number)?.toInt() ?: 30)
        return mapOf("status" to "not_implemented", "message" to "Audio recording ($duration sec)")
    }

    fun recordScreen(params: Map<*, *>): Any {
        val duration = ((params["duration"] as? Number)?.toInt() ?: 60)
        return mapOf("status" to "not_implemented", "message" to "Screen recording ($duration sec)")
    }

    fun lockPhone(): Any {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (pm.isScreenOn) {
                val policy = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                policy.lockNow()
            }
            mapOf("status" to "success")
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to "Lock requires device admin: ${e.message}")
        }
    }

    fun setVolume(params: Map<*, *>): Any {
        return try {
            val level = ((params["level"] as? Number)?.toInt() ?: 5)
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val streamType = AudioManager.STREAM_MUSIC
            am.setStreamVolume(streamType, level.coerceIn(0, am.getStreamMaxVolume(streamType)), 0)
            mapOf("status" to "success", "level" to level)
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun setBrightness(params: Map<*, *>): Any {
        return try {
            val level = ((params["level"] as? Number)?.toInt() ?: 128)
            val resolver = context.contentResolver
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, level.coerceIn(0, 255))
            mapOf("status" to "success", "brightness" to level)
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to "Requires WRITE_SETTINGS: ${e.message}")
        }
    }

    fun openUrl(params: Map<*, *>): Any {
        return try {
            val url = params["url"] as? String ?: return mapOf("status" to "error", "message" to "URL required")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            mapOf("status" to "success", "url" to url)
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun sendSMS(params: Map<*, *>): Any {
        return try {
            val number = params["number"] as? String ?: return mapOf("status" to "error", "message" to "Number required")
            val message = params["message"] as? String ?: ""
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(number, null, message, null, null)
            mapOf("status" to "success", "to" to number)
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun makeCall(params: Map<*, *>): Any {
        return try {
            val number = params["number"] as? String ?: return mapOf("status" to "error", "message" to "Number required")
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            mapOf("status" to "success", "number" to number)
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun speakText(params: Map<*, *>): Any {
        return try {
            val text = params["text"] as? String ?: return mapOf("status" to "error", "message" to "Text required")
            var ttsRef: TextToSpeech? = null
            ttsRef = TextToSpeech(context.applicationContext, TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsRef?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts1")
                }
            })
            mapOf("status" to "success", "text" to text)
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun showNotification(params: Map<*, *>): Any {
        return try {
            val title = params["title"] as? String ?: "Notification"
            val body = params["body"] as? String ?: ""
            Toast.makeText(context, "$title: $body", Toast.LENGTH_LONG).show()
            mapOf("status" to "success", "title" to title, "body" to body)
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun playSound(params: Map<*, *>): Any {
        return mapOf("status" to "success", "sound" to (params["sound"] as? String ?: "notification"))
    }

    fun toggleWifi(enable: Boolean): Any {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            wm.isWifiEnabled = enable
            mapOf("status" to "success", "wifi" to (if (enable) "enabled" else "disabled"))
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun toggleBluetooth(enable: Boolean): Any {
        return try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothAdapter
            if (enable) bm.enable() else bm.disable()
            mapOf("status" to "success", "bluetooth" to (if (enable) "enabled" else "disabled"))
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to "Requires BLUETOOTH_CONNECT")
        }
    }

    fun toggleAirplane(enable: Boolean): Any {
        return try {
            Settings.Global.putInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, if (enable) 1 else 0)
            val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            intent.putExtra("state", enable)
            context.sendBroadcast(intent)
            mapOf("status" to "success", "airplane" to (if (enable) "on" else "off"))
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to "Requires WRITE_SETTINGS")
        }
    }

    fun toggleTorch(on: Boolean): Any {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                cameraManager.setTorchMode(cameraManager.cameraIdList[0], on)
            }
            mapOf("status" to "success", "torch" to (if (on) "on" else "off"))
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun reboot(): Any {
        return try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot"))
            mapOf("status" to "success", "message" to "Rebooting (requires root)")
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to "Requires root: ${e.message}")
        }
    }

    fun shutdown(): Any {
        return try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "shutdown"))
            mapOf("status" to "success", "message" to "Shutting down (requires root)")
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to "Requires root: ${e.message}")
        }
    }

    fun openApp(params: Map<*, *>): Any {
        return try {
            val packageName = params["package"] as? String ?: return mapOf("status" to "error", "message" to "Package name required")
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return mapOf("status" to "error", "message" to "App not found: $packageName")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            mapOf("status" to "success", "package" to packageName)
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun closeApp(params: Map<*, *>): Any {
        return try {
            val packageName = params["package"] as? String ?: return mapOf("status" to "error", "message" to "Package name required")
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.killBackgroundProcesses(packageName)
            mapOf("status" to "success", "package" to packageName)
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun uninstallApp(params: Map<*, *>): Any {
        val packageName = params["package"] as? String ?: return mapOf("status" to "error", "message" to "Package required")
        return try {
            val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            mapOf("status" to "success", "package" to packageName)
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }

    fun forceStopApp(params: Map<*, *>): Any {
        val packageName = params["package"] as? String ?: return mapOf("status" to "error", "message" to "Package required")
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.killBackgroundProcesses(packageName)
            mapOf("status" to "success", "package" to packageName)
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to e.message)
        }
    }
}
