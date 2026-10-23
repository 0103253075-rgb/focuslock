package com.focuslock

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/**
 * Robust Singleton Overlay Manager.
 * Operates safely on Looper.getMainLooper() inside the active process.
 * Eliminates background Service start restrictions on modern Android (API 31-34).
 */
object LockOverlayManager {

    private const val TAG = "LockOverlayManager"
    const val MASTER_PIN = "884422"

    @Volatile
    var isOverlayShowing: Boolean = false
        private set

    private var windowManager: WindowManager? = null
    private var rootLayout: FrameLayout? = null
    private var countDownTimer: CountDownTimer? = null

    private var savedRingerMode: Int = AudioManager.RINGER_MODE_NORMAL
    private var savedRingVol: Int = 5
    private var savedNotifVol: Int = 5

    // 5-Tap Detection
    private var tapCount = 0
    private var lastTapTime = 0L
    private var keypadOpenTime = 0L

    // In-overlay PIN state
    private var enteredPin: String = ""
    private var pinDotsText: TextView? = null
    private var normalLockLayout: LinearLayout? = null
    private var pinPadLayout: LinearLayout? = null
    private var appContext: Context? = null

    fun show(context: Context, durationMinutes: Int, reason: String) {
        val app = context.applicationContext
        appContext = app

        Handler(Looper.getMainLooper()).post {
            try {
                if (isOverlayShowing && rootLayout != null) {
                    Log.i(TAG, "Overlay already visible, ignoring redundant show request.")
                    return@post
                }

                // Check overlay permission safely without throwing
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(app)) {
                    Log.w(TAG, "Screen Shield permission not granted by user!")
                    Toast.makeText(app, "Please grant Screen Shield permission to lock phone!", Toast.LENGTH_LONG).show()
                    return@post
                }

                windowManager = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val notifManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                // Total Silence safely wrapped
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
                    Log.w(TAG, "Could not set silent mode (non-critical): ${e.message}")
                }

                val wmParams = WindowManager.LayoutParams(
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
                    wmParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }

                // Custom touch intercepting root layout (Guarantees 5-tap detection anywhere)
                val frame = object : FrameLayout(app) {
                    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
                        if (ev?.action == MotionEvent.ACTION_DOWN) {
                            if (pinPadLayout?.visibility != View.VISIBLE) {
                                val now = System.currentTimeMillis()
                                if (now - lastTapTime < 900) {
                                    tapCount++
                                } else {
                                    tapCount = 1
                                }
                                lastTapTime = now

                                if (tapCount >= 5) {
                                    tapCount = 0
                                    enteredPin = ""
                                    keypadOpenTime = System.currentTimeMillis()
                                    updatePinDisplay()
                                    normalLockLayout?.visibility = View.GONE
                                    pinPadLayout?.visibility = View.VISIBLE
                                    return true // Consume 5th tap
                                }
                            }
                        }
                        return super.dispatchTouchEvent(ev)
                    }
                }.apply {
                    setBackgroundColor(Color.parseColor("#050507"))
                    setPadding(40, 70, 40, 40)

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
                rootLayout = frame

                // ==========================================
                // 1. MINIMALIST AMOLED LOCK VIEW
                // ==========================================
                val normalLayout = LinearLayout(app).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                normalLockLayout = normalLayout

                val topStatusText = TextView(app).apply {
                    text = "● FOCUS SHIELD ACTIVE"
                    textSize = 11f
                    setTextColor(Color.parseColor("#10b981"))
                    typeface = Typeface.MONOSPACE
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, 40)
                }

                val lockOrb = TextView(app).apply {
                    text = "🔒"
                    textSize = 42f
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, 30)
                }

                val quoteText = TextView(app).apply {
                    text = "\"My lack of discipline is an insult to everyone who believes in me.\""
                    textSize = 19f
                    setTextColor(Color.parseColor("#f4f4f5"))
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setLineSpacing(6f, 1.25f)
                    setPadding(20, 0, 20, 35)
                }

                val divider = View(app).apply {
                    setBackgroundColor(Color.parseColor("#27272a"))
                    layoutParams = LinearLayout.LayoutParams(90, 3).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        setMargins(0, 0, 0, 35)
                    }
                }

                val timerText = TextView(app).apply {
                    text = "${durationMinutes}:00"
                    textSize = 50f
                    setTextColor(Color.parseColor("#e4e4e7"))
                    typeface = Typeface.MONOSPACE
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, 6)
                }

                val sessionLabel = TextView(app).apply {
                    text = "SESSION REMAINING"
                    textSize = 10f
                    setTextColor(Color.parseColor("#71717a"))
                    typeface = Typeface.MONOSPACE
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, 40)
                }

                val callNoticeText = TextView(app).apply {
                    text = "📞 Smart Call Bypass Active\n(Tap screen 5 times for emergency Master PIN)"
                    textSize = 11f
                    setTextColor(Color.parseColor("#52525b"))
                    typeface = Typeface.MONOSPACE
                    gravity = Gravity.CENTER
                    setLineSpacing(4f, 1f)
                }

                normalLayout.addView(topStatusText)
                normalLayout.addView(lockOrb)
                normalLayout.addView(quoteText)
                normalLayout.addView(divider)
                normalLayout.addView(timerText)
                normalLayout.addView(sessionLabel)
                normalLayout.addView(callNoticeText)

                // ==========================================
                // 2. IN-OVERLAY PIN PAD VIEW (5-Tap Triggered)
                // ==========================================
                val pinLayout = LinearLayout(app).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    visibility = View.GONE
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                pinPadLayout = pinLayout

                val pinTitle = TextView(app).apply {
                    text = "Emergency Master Unlock"
                    textSize = 20f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, 8)
                }

                val pinDesc = TextView(app).apply {
                    text = "Enter Master PIN (884422)"
                    textSize = 12f
                    setTextColor(Color.parseColor("#71717a"))
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, 20)
                }

                val dotsView = TextView(app).apply {
                    text = "_ _ _ _ _ _"
                    textSize = 28f
                    typeface = Typeface.MONOSPACE
                    setTextColor(Color.parseColor("#00F0FF"))
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, 25)
                }
                pinDotsText = dotsView

                pinLayout.addView(pinTitle)
                pinLayout.addView(pinDesc)
                pinLayout.addView(dotsView)

                val rows = arrayOf(
                    arrayOf("1", "2", "3"),
                    arrayOf("4", "5", "6"),
                    arrayOf("7", "8", "9"),
                    arrayOf("BACK", "0", "DEL")
                )

                for (row in rows) {
                    val rowLayout = LinearLayout(app).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, 0, 0, 10) }
                    }

                    for (btnText in row) {
                        val btn = Button(app).apply {
                            text = btnText
                            textSize = 18f
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(Color.WHITE)
                            setBackgroundColor(if (btnText == "BACK" || btnText == "DEL") Color.parseColor("#27272a") else Color.parseColor("#18181b"))
                            layoutParams = LinearLayout.LayoutParams(180, 140).apply {
                                setMargins(8, 0, 8, 0)
                            }
                            setOnClickListener {
                                if (System.currentTimeMillis() - keypadOpenTime < 350) {
                                    return@setOnClickListener
                                }
                                when (btnText) {
                                    "BACK" -> {
                                        enteredPin = ""
                                        updatePinDisplay()
                                        pinPadLayout?.visibility = View.GONE
                                        normalLockLayout?.visibility = View.VISIBLE
                                    }
                                    "DEL" -> {
                                        if (enteredPin.isNotEmpty()) {
                                            enteredPin = enteredPin.dropLast(1)
                                        }
                                        updatePinDisplay()
                                    }
                                    else -> {
                                        if (enteredPin.length < 6) {
                                            enteredPin += btnText.trim()
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
                    pinLayout.addView(rowLayout)
                }

                val submitUnlockButton = Button(app).apply {
                    text = "UNLOCK PHONE"
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#09090b"))
                    setBackgroundColor(Color.parseColor("#00F0FF"))
                    layoutParams = LinearLayout.LayoutParams(560, 130).apply {
                        setMargins(0, 15, 0, 0)
                    }
                    setOnClickListener {
                        if (System.currentTimeMillis() - keypadOpenTime < 350) {
                            return@setOnClickListener
                        }
                        checkPinAndUnlock()
                    }
                }
                pinLayout.addView(submitUnlockButton)

                frame.addView(normalLayout)
                frame.addView(pinLayout)

                // Add to WindowManager safely
                try {
                    windowManager?.addView(frame, wmParams)
                    isOverlayShowing = true
                    EmergencyCallReceiver.isFocusLockActive = true
                    Log.i(TAG, "Shield overlay successfully displayed on screen.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding overlay to WindowManager: ${e.message}", e)
                    isOverlayShowing = false
                    return@post
                }

                // Countdown Timer
                val totalMillis = durationMinutes * 60 * 1000L
                countDownTimer?.cancel()
                countDownTimer = object : CountDownTimer(totalMillis, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val mins = (millisUntilFinished / 1000) / 60
                        val secs = (millisUntilFinished / 1000) % 60
                        timerText.text = String.format(Locale.US, "%02d:%02d", mins, secs)
                    }

                    override fun onFinish() {
                        Toast.makeText(app, "⏰ Focus session ended! Phone safely unlocked.", Toast.LENGTH_LONG).show()
                        dismiss(app)
                    }
                }.start()

            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in showOverlay: ${e.message}", e)
            }
        }
    }

    private fun updatePinDisplay() {
        val len = enteredPin.length
        val sb = StringBuilder()
        for (i in 0 until 6) {
            if (i < len) sb.append(enteredPin[i]).append(" ")
            else sb.append("_ ")
        }
        pinDotsText?.text = sb.toString().trim()
    }

    private fun checkPinAndUnlock() {
        if (enteredPin.trim() == MASTER_PIN.trim()) {
            val app = appContext
            if (app != null) {
                Toast.makeText(app, "🔑 Master PIN Verified. Unlocking phone!", Toast.LENGTH_SHORT).show()
            }
            try {
                FocusSyncService.notifyPinUnlocked(MASTER_PIN)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            dismiss(app)
            goToHomeScreen(app)
        } else {
            val app = appContext
            if (app != null) {
                Toast.makeText(app, "Incorrect PIN ($enteredPin)! Try 884422", Toast.LENGTH_SHORT).show()
            }
            enteredPin = ""
            updatePinDisplay()
        }
    }

    fun dismiss(context: Context? = null) {
        val targetContext = context?.applicationContext ?: appContext
        Handler(Looper.getMainLooper()).post {
            try {
                countDownTimer?.cancel()
                countDownTimer = null

                // Restore Audio
                if (targetContext != null) {
                    try {
                        val audioManager = targetContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val notifManager = targetContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                        audioManager.ringerMode = savedRingerMode
                        audioManager.setStreamVolume(AudioManager.STREAM_RING, savedRingVol, 0)
                        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, savedNotifVol, 0)

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notifManager.isNotificationPolicyAccessGranted) {
                            notifManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Audio restore error (non-critical): ${e.message}")
                    }
                }

                // Remove View from WindowManager safely
                if (rootLayout != null && windowManager != null) {
                    try {
                        windowManager?.removeViewImmediate(rootLayout)
                    } catch (e: Exception) {
                        try {
                            windowManager?.removeView(rootLayout)
                        } catch (ex: Exception) {}
                    }
                    rootLayout = null
                }

                isOverlayShowing = false
                EmergencyCallReceiver.isFocusLockActive = false
                Log.i(TAG, "Shield overlay dismissed.")
            } catch (e: Exception) {
                Log.e(TAG, "Error in dismiss: ${e.message}", e)
            }
        }
    }

    private fun goToHomeScreen(context: Context?) {
        if (context == null) return
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(homeIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
