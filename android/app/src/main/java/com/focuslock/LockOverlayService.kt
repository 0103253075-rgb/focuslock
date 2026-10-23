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
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Safe Floating Focus Overlay:
 * - Covers screen with an AMOLED pitch-black focus interface.
 * - Hardware Fail-safe: Auto-unlocks independently when time expires.
 * - Emergency Escape Hatch: Tap 5 times to reveal Emergency Master PIN.
 */
class LockOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var countDownTimer: CountDownTimer? = null
    private var tapCount = 0
    private var lastTapTime = 0L
    private var savedRingerMode: Int = AudioManager.RINGER_MODE_NORMAL
    private var savedRingVol: Int = 5
    private var savedNotifVol: Int = 5

    companion object {
        const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"
        const val EXTRA_REASON = "extra_reason"
        const val ACTION_UNLOCK = "com.focuslock.ACTION_UNLOCK"
        var isOverlayShowing = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_UNLOCK) {
            removeOverlay()
            stopSelf()
            return START_NOT_STICKY
        }

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

        // 1. Save and apply TOTAL SILENCE
        try {
            savedRingerMode = audioManager.ringerMode
            savedRingVol = audioManager.getStreamVolume(AudioManager.STREAM_RING)
            savedNotifVol = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)

            // Engage Do Not Disturb (Priority mode: silences all alerts & notifications)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notifManager.isNotificationPolicyAccessGranted) {
                notifManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }

            // Force silent ringer and mute notification/system channels
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

        // Programmatic AMOLED black layout
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#09090b"))
            setPadding(60, 60, 60, 60)

            // Hide system status and navigation bars completely
            @Suppress("DEPRECATION")
            systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )

            // 5-Tap Emergency Escape Hatch
            setOnClickListener {
                val now = System.currentTimeMillis()
                if (now - lastTapTime < 800) {
                    tapCount++
                } else {
                    tapCount = 1
                }
                lastTapTime = now

                if (tapCount >= 5) {
                    tapCount = 0
                    val pinIntent = Intent(this@LockOverlayService, EmergencyPinActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(pinIntent)
                }
            }
        }

        // Lock Icon Text
        val iconText = TextView(this).apply {
            text = "🔒"
            textSize = 54f
            gravity = Gravity.CENTER
        }

        // Title
        val titleText = TextView(this).apply {
            text = "FOCUS LOCKDOWN ACTIVE"
            textSize = 18f
            setTextColor(Color.parseColor("#00F0FF"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 10)
        }

        // Timer Display
        val timerText = TextView(this).apply {
            text = "${durationMinutes}:00"
            textSize = 48f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 20)
        }

        // Emotional Accountability Prompt
        val quoteText = TextView(this).apply {
            text = "Did you make your parents proud today?"
            textSize = 15f
            setTextColor(Color.parseColor("#f43f5e"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 30)
        }

        // Emergency Notice
        val callNoticeText = TextView(this).apply {
            text = "🛡️ Emergency Bypass:\nCalls are muted unless someone rings twice within 5 minutes.\n\n(Tap screen 5 times for emergency Master PIN)"
            textSize = 12f
            setTextColor(Color.parseColor("#71717a"))
            gravity = Gravity.CENTER
            setLineSpacing(6f, 1f)
        }

        container.addView(iconText)
        container.addView(titleText)
        container.addView(timerText)
        container.addView(quoteText)
        container.addView(callNoticeText)

        overlayView = container
        windowManager?.addView(overlayView, layoutParams)
        isOverlayShowing = true
        EmergencyCallReceiver.isFocusLockActive = true

        // Hardware Fail-Safe Timer (Counts down on the phone independently)
        val totalMillis = durationMinutes * 60 * 1000L
        countDownTimer = object : CountDownTimer(totalMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val mins = (millisUntilFinished / 1000) / 60
                val secs = (millisUntilFinished / 1000) % 60
                timerText.text = String.format("%02d:%02d", mins, secs)
            }

            override fun onFinish() {
                // Auto-unlock fail-safe
                Toast.makeText(this@LockOverlayService, "Focus Session Complete! Unlocked safely.", Toast.LENGTH_LONG).show()
                removeOverlay()
                stopSelf()
            }
        }.start()
    }

    private fun removeOverlay() {
        countDownTimer?.cancel()
        countDownTimer = null

        // Restore sound and DND settings
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

        if (overlayView != null && windowManager != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                // View already detached
            }
            overlayView = null
        }
        isOverlayShowing = false
        EmergencyCallReceiver.isFocusLockActive = false
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }
}
