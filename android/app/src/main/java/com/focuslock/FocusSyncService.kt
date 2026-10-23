package com.focuslock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Background Foreground Service:
 * - Connects wirelessly to laptop WebSocket server.
 * - Listens for LOCK / UNLOCK commands.
 * - Reports device battery and connectivity.
 */
class FocusSyncService : Service() {

    companion object {
        private const val TAG = "FocusLock_Sync"
        private const val CHANNEL_ID = "focus_lock_sync_channel"
        private const val NOTIFICATION_ID = 1001

        private var activeWebSocket: WebSocket? = null

        @Volatile
        var isLocalUnlocked: Boolean = false

        fun notifyPinUnlocked(pin: String) {
            isLocalUnlocked = true
            try {
                val json = JSONObject().apply {
                    put("type", "PHONE_PIN_UNLOCK")
                    put("pin", pin)
                }
                activeWebSocket?.send(json.toString())
                Log.i(TAG, "Sent PHONE_PIN_UNLOCK to laptop server")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send PIN unlock to WS", e)
            }
        }

        fun sendCallEvent(number: String, isRepeatCaller: Boolean, action: String) {
            try {
                val json = JSONObject().apply {
                    put("type", "CALL_EVENT")
                    put("callerNumber", number)
                    put("isRepeatCaller", isRepeatCaller)
                    put("action", action)
                }
                activeWebSocket?.send(json.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send call event over WS", e)
            }
        }
    }

    private var client: OkHttpClient? = null
    private var serverUrl: String = "wss://catalogs-alex-voted-invision.trycloudflare.com"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("FocusLock Wireless Sync Active"))
        setupWebSocketClient()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val customIp = intent?.getStringExtra("server_ip")?.trim()
        if (!customIp.isNullOrEmpty()) {
            var formatted = customIp
            if (formatted.startsWith("http://")) formatted = formatted.replace("http://", "ws://")
            else if (formatted.startsWith("https://")) formatted = formatted.replace("https://", "wss://")
            else if (!formatted.startsWith("ws://") && !formatted.startsWith("wss://")) {
                if (formatted.contains("trycloudflare.com")) {
                    formatted = "wss://$formatted"
                } else {
                    formatted = if (formatted.contains(":")) "ws://$formatted" else "ws://$formatted:4000"
                }
            }
            serverUrl = formatted
            Log.i(TAG, "Configured serverUrl: $serverUrl")
            connectWebSocket()
        }
        return START_STICKY
    }

    private fun setupWebSocketClient() {
        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        connectWebSocket()
    }

    private fun connectWebSocket() {
        try {
            activeWebSocket?.close(1000, "Reconnecting")
            activeWebSocket = null

            val request = Request.Builder().url(serverUrl).build()
            client?.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.i(TAG, "Connected to Laptop Sync Server at $serverUrl")
                    activeWebSocket = webSocket
                    sendHeartbeat(webSocket)
                    android.os.Handler(mainLooper).post {
                        android.widget.Toast.makeText(applicationContext, "🟢 Connected to Laptop FocusLock!", android.widget.Toast.LENGTH_LONG).show()
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleIncomingServerMessage(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    activeWebSocket = null
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "Sync server disconnected: ${t.message}, retrying in 4s...")
                    activeWebSocket = null
                    // Reconnect in 4 seconds
                    android.os.Handler(mainLooper).postDelayed({ connectWebSocket() }, 4000)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Connection error: ${e.message}")
        }
    }

    private fun handleIncomingServerMessage(jsonString: String) {
        try {
            val root = JSONObject(jsonString)
            if (root.optString("type") == "STATE_UPDATE") {
                val state = root.getJSONObject("state")
                val isLocked = state.getBoolean("isLocked")
                val durationMins = state.optInt("sessionDurationMinutes", 45)
                val reason = state.optString("lockReason", "Focus Session")

                if (isLocked) {
                    if (isLocalUnlocked) {
                        // User manually entered Master PIN on their phone!
                        // Do NOT re-lock the phone.
                        return
                    }
                    if (!LockOverlayService.isOverlayShowing) {
                        val lockIntent = Intent(this, LockOverlayService::class.java).apply {
                            putExtra(LockOverlayService.EXTRA_DURATION_MINUTES, durationMins)
                            putExtra(LockOverlayService.EXTRA_REASON, reason)
                        }
                        startService(lockIntent)
                    }
                } else {
                    isLocalUnlocked = false
                    // Unconditionally dismiss overlay immediately in 0ms
                    LockOverlayService.dismissNow()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message", e)
        }
    }

    private fun sendHeartbeat(webSocket: WebSocket) {
        val batteryStatus: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 85
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val batteryPct = (level * 100 / scale.toFloat()).toInt()

        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val heartbeat = JSONObject().apply {
            put("type", "DEVICE_HEARTBEAT")
            put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("batteryLevel", batteryPct)
            put("isCharging", isCharging)
        }
        webSocket.send(heartbeat.toString())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FocusLock Wireless Sync",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("FocusLock")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("FocusLock")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build()
        }
    }
}
