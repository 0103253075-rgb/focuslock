package com.focuslock

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class LockOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayContainer: LinearLayout? = null
    private var countDownTimer: CountDownTimer? = null
    private var savedRingerMode: Int = AudioManager.RINGER_MODE_NORMAL
    private var savedRingVol: Int = 5
    private var savedNotifVol: Int = 5

    // In-overlay PIN state
    private var enteredPin = StringBuilder()
    private lateinit var pinDotsText: TextView
    private lateinit var normalLockLayout: LinearLayout
    private lateinit var pinPadLayout: LinearLayout

    companion object {
        const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"
        const val EXTRA_REASON = "extra_reason"
        const val MASTER_PIN = "884422"

        @Volatile
        var currentInstance: LockOverlayService? = null
        var isOverlayShowing = false

        /**
         * Instant dismiss called from WebSocket sync or PIN unlock
         */
        fun dismissNow() {
            android.os.Handler(Looper.getMainLooper()).post {
                currentInstance?.removeOverlay()
                currentInstance?.stopSelf()
                currentInstance = null
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        currentInstance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentInstance = this
        val durationMinutes = intent?.getIntExtra(EXTRA_DURATION_MINUTES, 45) ?: 45
        val reason = intent?.getStringExtra(EXTRA_REASON) ?: "Deep Focus"

        showOverlay(durationMinutes, reason)
        return START_STICKY
    }

    private fun showOverlay(durationMinutes: Int, reason: String) {
        if (isOverlayShowing) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1. Save and apply Total Silence
        try {
            savedRingerMode = audioManager.ringerMode
            savedRingVol = audioManager.getStreamVolume(AudioManager.STREAM_RING)
            savedNotifVol = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notifManager.isNotificationPolicyAccessGranted) {
                notifManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }

            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Master Container
        overlayContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#09090b"))
            setPadding(40, 60, 40, 40)

            @Suppress("DEPRECATION")
            systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }

        // ==========================================
        // 1. NORMAL LOCK VIEW
        // ==========================================
        normalLockLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val iconText = TextView(this).apply {
            text = "🔒"
            textSize = 54f
            gravity = Gravity.CENTER
        }

        val titleText = TextView(this).apply {
            text = "FOCUS LOCKDOWN ACTIVE"
            textSize = 18f
            setTextColor(Color.parseColor("#00F0FF"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 10)
        }

        val timerText = TextView(this).apply {
            text = "${durationMinutes}:00"
            textSize = 52f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 20)
        }

        val quoteText = TextView(this).apply {
            text = "\"Did you make your parents proud today?\""
            textSize = 16f
            setTextColor(Color.parseColor("#f43f5e"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 25)
        }

        val emergencyBypassButton = Button(this).apply {
            text = "🔑 Emergency PIN Unlock"
            setBackgroundColor(Color.parseColor("#18181b"))
            setTextColor(Color.parseColor("#a1a1aa"))
            textSize = 12f
            setPadding(30, 20, 30, 20)
            setOnClickListener {
                enteredPin.clear()
                updatePinDisplay()
                normalLockLayout.visibility = View.GONE
                pinPadLayout.visibility = View.VISIBLE
            }
        }

        normalLockLayout.addView(iconText)
        normalLockLayout.addView(titleText)
        normalLockLayout.addView(timerText)
        normalLockLayout.addView(quoteText)
        normalLockLayout.addView(emergencyBypassButton)

        // ==========================================
        // 2. IN-OVERLAY PIN PAD VIEW (Fail-safe)
        // ==========================================
        pinPadLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val pinTitle = TextView(this).apply {
            text = "Emergency Master Unlock"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }

        val pinDesc = TextView(this).apply {
            text = "Enter Master PIN (Default: 884422)"
            textSize = 12f
            setTextColor(Color.parseColor("#71717a"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }

        pinDotsText = TextView(this).apply {
            text = "_ _ _ _ _ _"
            textSize = 28f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#00F0FF"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 25)
        }

        pinPadLayout.addView(pinTitle)
        pinPadLayout.addView(pinDesc)
        pinPadLayout.addView(pinDotsText)

        // Numeric Keypad Grid (Rows 1-3, 4-6, 7-9, Cancel/0/Del)
        val rows = arrayOf(
            arrayOf("1", "2", "3"),
            arrayOf("4", "5", "6"),
            arrayOf("7", "8", "9"),
            arrayOf("BACK", "0", "CLEAR")
        )

        for (row in rows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 10) }
            }

            for (btnText in row) {
                val btn = Button(this).apply {
                    text = btnText
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    setBackgroundColor(if (btnText == "BACK" || btnText == "CLEAR") Color.parseColor("#27272a") else Color.parseColor("#18181b"))
                    layoutParams = LinearLayout.LayoutParams(180, 140).apply {
                        setMargins(8, 0, 8, 0)
                    }
                    setOnClickListener {
                        when (btnText) {
                            "BACK" -> {
                                pinPadLayout.visibility = View.GONE
                                normalLockLayout.visibility = View.VISIBLE
                            }
                            "CLEAR" -> {
                                if (enteredPin.isNotEmpty()) enteredPin.deleteCharAt(enteredPin.length - 1)
                                updatePinDisplay()
                            }
                            else -> {
                                if (enteredPin.length < 6) {
                                    enteredPin.append(btnText)
                                    updatePinDisplay()
                                    if (enteredPin.length == 6) {
                                        checkPinAndUnlock()
                                    }
                                }
                            }
                        }
                    }
                }
                rowLayout.addView(btn)
            }
            pinPadLayout.addView(rowLayout)
        }

        overlayContainer?.addView(normalLockLayout)
        overlayContainer?.addView(pinPadLayout)

        windowManager?.addView(overlayContainer, layoutParams)
        isOverlayShowing = true
        EmergencyCallReceiver.isFocusLockActive = true

        // Independent Hardware Timer
        val totalMillis = durationMinutes * 60 * 1000L
        countDownTimer = object : CountDownTimer(totalMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val mins = (millisUntilFinished / 1000) / 60
                val secs = (millisUntilFinished / 1000) % 60
                timerText.text = String.format("%02d:%02d", mins, secs)
            }

            override fun onFinish() {
                Toast.makeText(this@LockOverlayService, "⏰ Focus session ended! Phone safely unlocked.", Toast.LENGTH_LONG).show()
                dismissNow()
            }
        }.start()
    }

    private fun updatePinDisplay() {
        val len = enteredPin.length
        val sb = StringBuilder()
        for (i in 0 until 6) {
            if (i < len) sb.append("• ")
            else sb.append("_ ")
        }
        pinDotsText.text = sb.toString().trim()
    }

    private fun checkPinAndUnlock() {
        if (enteredPin.toString() == MASTER_PIN) {
            Toast.makeText(this, "🔑 Master PIN Verified. Unlocking phone!", Toast.LENGTH_SHORT).show()
            dismissNow()
        } else {
            Toast.makeText(this, "Incorrect PIN! Try 884422", Toast.LENGTH_SHORT).show()
            enteredPin.clear()
            updatePinDisplay()
        }
    }

    fun removeOverlay() {
        countDownTimer?.cancel()
        countDownTimer = null

        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            audioManager.ringerMode = savedRingerMode
            audioManager.setStreamVolume(AudioManager.STREAM_RING, savedRingVol, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, savedNotifVol, 0)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notifManager.isNotificationPolicyAccessGranted) {
                notifManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (overlayContainer != null && windowManager != null) {
            try {
                windowManager?.removeView(overlayContainer)
            } catch (e: Exception) {}
            overlayContainer = null
        }
        isOverlayShowing = false
        EmergencyCallReceiver.isFocusLockActive = false
        currentInstance = null
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }
}
