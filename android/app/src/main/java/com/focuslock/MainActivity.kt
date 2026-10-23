package com.focuslock

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var overlayButton: Button
    private lateinit var dndButton: Button
    private lateinit var callPermButton: Button

    // Follower edition duration controls
    private var selectedDuration = 45
    private var btn25: Button? = null
    private var btn45: Button? = null
    private var btn60: Button? = null
    private var btnCustom: Button? = null
    private var customMinutesInput: EditText? = null
    private var startLockdownButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isFollowerEdition = BuildConfig.FLAVOR == "follower"

        val prefs = getSharedPreferences("focuslock_prefs", Context.MODE_PRIVATE)
        var initialCode = prefs.getString("sync_code", "") ?: ""
        var initialHost = prefs.getString("sync_host", "") ?: ""

        val intentData: Uri? = intent?.data
        if (intentData != null && intentData.scheme == "focuslock") {
            val r = intentData.getQueryParameter("room")
            val h = intentData.getQueryParameter("host")
            if (!r.isNullOrEmpty()) initialCode = r
            if (!h.isNullOrEmpty()) initialHost = h
        }

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#09090b"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(40, 50, 40, 40)
        }
        scrollView.addView(root)

        // Logo & Title
        val logo = TextView(this).apply {
            text = "🛡️ FocusLock"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 6)
        }

        val subtitle = TextView(this).apply {
            text = if (isFollowerEdition) "Discipline & Deep Work Studio" else "Remote Lockdown Studio (Laptop Controlled)"
            textSize = 13f
            setTextColor(Color.parseColor("#71717a"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }

        statusText = TextView(this).apply {
            text = "Grant permissions below to enable screen shield"
            textSize = 12f
            setTextColor(Color.parseColor("#f59e0b"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }

        // Section 1: Permissions
        val permTitle = TextView(this).apply {
            text = "STEP 1: PERMISSION SETUP"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#a1a1aa"))
            gravity = Gravity.START
            setPadding(10, 0, 0, 10)
        }

        val marginParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, 12)
        }

        // Button 1: Overlay Permission
        overlayButton = Button(this).apply {
            text = "1. Screen Shield (Draw Over Apps)"
            setBackgroundColor(Color.parseColor("#18181b"))
            setTextColor(Color.WHITE)
            textSize = 12f
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    Toast.makeText(this@MainActivity, "Screen Shield already granted!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Button 2: Do Not Disturb
        dndButton = Button(this).apply {
            text = "2. Total Silence (DND Access)"
            setBackgroundColor(Color.parseColor("#18181b"))
            setTextColor(Color.WHITE)
            textSize = 12f
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (!nm.isNotificationPolicyAccessGranted) {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    } else {
                        Toast.makeText(this@MainActivity, "Do Not Disturb access already granted!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Button 3: Call Screening
        callPermButton = Button(this).apply {
            text = "3. Emergency Call Filter (Phone State)"
            setBackgroundColor(Color.parseColor("#18181b"))
            setTextColor(Color.WHITE)
            textSize = 12f
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(
                        arrayOf(
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.READ_CALL_LOG
                        ),
                        101
                    )
                }
            }
        }

        root.addView(logo)
        root.addView(subtitle)
        root.addView(statusText)
        root.addView(permTitle)
        root.addView(overlayButton, marginParams)
        root.addView(dndButton, marginParams)
        root.addView(callPermButton, marginParams)

        if (isFollowerEdition) {
            // Follower Edition: Standalone Duration selector & Start Lockdown button
            val durationTitle = TextView(this).apply {
                text = "STEP 2: CHOOSE SESSION DURATION"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#a1a1aa"))
                gravity = Gravity.START
                setPadding(10, 20, 0, 10)
            }

            val durationRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 16) }
            }

            val b25 = createDurationButton("25m\nPomodoro", 25)
            val b45 = createDurationButton("45m\nDeep", 45)
            val b60 = createDurationButton("60m\nUltra", 60)
            val bCustom = createDurationButton("Custom\nMins", -1)
            btn25 = b25
            btn45 = b45
            btn60 = b60
            btnCustom = bCustom

            durationRow.addView(b25)
            durationRow.addView(b45)
            durationRow.addView(b60)
            durationRow.addView(bCustom)

            val customInput = EditText(this).apply {
                hint = "Enter Custom Minutes (e.g. 100)"
                setText("100")
                setHintTextColor(Color.parseColor("#52525b"))
                setTextColor(Color.parseColor("#00F0FF"))
                textSize = 14f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#18181b"))
                setPadding(16, 20, 16, 20)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                visibility = android.view.View.GONE
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                        val m = s?.toString()?.toIntOrNull() ?: 100
                        if (selectedDuration == -1) {
                            startLockdownButton?.text = "⚡ START FOCUS LOCKDOWN (${m}m)"
                        }
                    }
                    override fun afterTextChanged(s: android.text.Editable?) {}
                })
            }
            customMinutesInput = customInput

            val startBtn = Button(this).apply {
                text = "⚡ START FOCUS LOCKDOWN (45m)"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#09090b"))
                setBackgroundColor(Color.parseColor("#00F0FF"))
                setPadding(20, 32, 20, 32)
                setOnClickListener {
                    startStandaloneLockdown()
                }
            }
            startLockdownButton = startBtn

            root.addView(durationTitle)
            root.addView(durationRow)
            root.addView(customInput, marginParams)
            root.addView(startBtn, marginParams)
            selectDuration(45)
        } else {
            // Creator Edition: 100% Laptop Controlled (No phone-side lockdown buttons)
            val connectTitle = TextView(this).apply {
                text = "STEP 2: PAIR WITH LAPTOP STUDIO"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#a1a1aa"))
                gravity = Gravity.START
                setPadding(10, 20, 0, 8)
            }

            val pairDesc = TextView(this).apply {
                text = "Open FocusLock on your laptop and enter the 4-digit Sync Code:"
                textSize = 12f
                setTextColor(Color.parseColor("#71717a"))
                gravity = Gravity.START
                setPadding(10, 0, 0, 10)
            }

            // Primary 4-Digit Code Input
            val codeInput = EditText(this).apply {
                hint = "e.g. 5821"
                setText(initialCode)
                setHintTextColor(Color.parseColor("#52525b"))
                setTextColor(Color.parseColor("#00F0FF"))
                textSize = 20f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#18181b"))
                setPadding(20, 24, 20, 24)
            }

            // Server Host (Optional, for Custom Cloud or Local Wi-Fi)
            val hostLabel = TextView(this).apply {
                text = "Server Host / Local IP (Optional - leave blank for Cloud):"
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#71717a"))
                gravity = Gravity.START
                setPadding(10, 14, 0, 6)
            }

            val hostInput = EditText(this).apply {
                hint = "Default: focuslock-7pe7.onrender.com (or local IP)"
                setText(initialHost)
                setHintTextColor(Color.parseColor("#52525b"))
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#18181b"))
                setPadding(16, 18, 16, 18)
            }

            val connectButton = Button(this).apply {
                text = "⚡ PAIR & CONNECT TO LAPTOP"
                setBackgroundColor(Color.parseColor("#00F0FF"))
                setTextColor(Color.parseColor("#09090b"))
                typeface = Typeface.DEFAULT_BOLD
                textSize = 14f
                setPadding(20, 28, 20, 28)
                setOnClickListener {
                    val code = codeInput.text.toString().trim()
                    val host = hostInput.text.toString().trim()

                    if (code.isEmpty() && host.isEmpty()) {
                        Toast.makeText(this@MainActivity, "Please enter your 4-digit Sync Code or Laptop IP!", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    // Save to preferences so user never has to retype
                    prefs.edit()
                        .putString("sync_code", code)
                        .putString("sync_host", host)
                        .apply()

                    val serviceIntent = Intent(this@MainActivity, FocusSyncService::class.java).apply {
                        putExtra("sync_code", code)
                        putExtra("server_host", host)
                        putExtra("server_ip", if (host.isNotEmpty()) host else code)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }

                    statusText.text = "🟢 Pairing with Laptop (Code: ${code.ifEmpty { "Local" }})..."
                    statusText.setTextColor(Color.parseColor("#10b981"))
                    Toast.makeText(this@MainActivity, "Connecting to Laptop Studio...", Toast.LENGTH_SHORT).show()
                }
            }

            val remoteHint = TextView(this).apply {
                text = "💡 Trigger and manage all lock sessions directly from your laptop dashboard."
                textSize = 11f
                setTextColor(Color.parseColor("#71717a"))
                gravity = Gravity.CENTER
                setPadding(0, 10, 0, 0)
            }

            root.addView(connectTitle)
            root.addView(pairDesc)
            root.addView(codeInput, marginParams)
            root.addView(hostLabel)
            root.addView(hostInput, marginParams)
            root.addView(connectButton, marginParams)
            root.addView(remoteHint)
        }

        // Safety Instructions Box
        val safetyBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121215"))
            setPadding(30, 25, 30, 25)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 20, 0, 20) }
        }

        val safetyTitle = TextView(this).apply {
            text = "🔒 Safety Guarantee & Controls"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 10)
        }

        val safetyDetails = TextView(this).apply {
            text = "• Emergency Master PIN: 884422\n• Tap locked screen 5 times rapidly to show keypad\n• Smart Call Pass: Important callers calling twice ring through\n• Auto-unlocks when session timer reaches 00:00"
            textSize = 11f
            setTextColor(Color.parseColor("#71717a"))
            setLineSpacing(5f, 1f)
        }

        safetyBox.addView(safetyTitle)
        safetyBox.addView(safetyDetails)
        root.addView(safetyBox)

        setContentView(scrollView)
    }

    private fun createDurationButton(label: String, mins: Int): Button {
        return Button(this).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, 130, 1f).apply {
                setMargins(6, 0, 6, 0)
            }
            setOnClickListener {
                selectDuration(mins)
            }
        }
    }

    private fun selectDuration(mins: Int) {
        selectedDuration = mins
        val activeBg = Color.parseColor("#00F0FF")
        val activeText = Color.parseColor("#09090b")
        val inactiveBg = Color.parseColor("#18181b")
        val inactiveText = Color.parseColor("#a1a1aa")

        btn25?.setBackgroundColor(if (mins == 25) activeBg else inactiveBg)
        btn25?.setTextColor(if (mins == 25) activeText else inactiveText)

        btn45?.setBackgroundColor(if (mins == 45) activeBg else inactiveBg)
        btn45?.setTextColor(if (mins == 45) activeText else inactiveText)

        btn60?.setBackgroundColor(if (mins == 60) activeBg else inactiveBg)
        btn60?.setTextColor(if (mins == 60) activeText else inactiveText)

        btnCustom?.setBackgroundColor(if (mins == -1) activeBg else inactiveBg)
        btnCustom?.setTextColor(if (mins == -1) activeText else inactiveText)

        if (mins == -1) {
            customMinutesInput?.visibility = android.view.View.VISIBLE
            val customVal = customMinutesInput?.text?.toString()?.toIntOrNull() ?: 100
            startLockdownButton?.text = "⚡ START FOCUS LOCKDOWN (${customVal}m)"
        } else {
            customMinutesInput?.visibility = android.view.View.GONE
            startLockdownButton?.text = "⚡ START FOCUS LOCKDOWN (${mins}m)"
        }
    }

    private fun startStandaloneLockdown() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant Screen Shield permission first!", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        val effectiveDuration = if (selectedDuration == -1) {
            val entered = customMinutesInput?.text?.toString()?.toIntOrNull() ?: 100
            Math.max(1, entered)
        } else {
            selectedDuration
        }

        val lockIntent = Intent(this, LockOverlayService::class.java).apply {
            putExtra(LockOverlayService.EXTRA_DURATION_MINUTES, effectiveDuration)
            putExtra(LockOverlayService.EXTRA_REASON, "Follower Focus")
        }
        startService(lockIntent)
        Toast.makeText(this, "Lockdown started for ${effectiveDuration} min! Stay disciplined.", Toast.LENGTH_SHORT).show()
        moveTaskToBack(true)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()
    }

    private fun updatePermissionStates() {
        // Check Overlay
        val canDraw = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        if (canDraw) {
            overlayButton.text = "✓ 1. Screen Shield (Granted)"
            overlayButton.setTextColor(Color.parseColor("#10b981"))
        } else {
            overlayButton.text = "1. Screen Shield (Required)"
            overlayButton.setTextColor(Color.WHITE)
        }

        // Check DND
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val canDnd = nm.isNotificationPolicyAccessGranted
            if (canDnd) {
                dndButton.text = "✓ 2. Total Silence (Granted)"
                dndButton.setTextColor(Color.parseColor("#10b981"))
            } else {
                dndButton.text = "2. Total Silence (Optional)"
                dndButton.setTextColor(Color.WHITE)
            }
        }

        // Check Call Perms
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val statePerm = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            if (statePerm) {
                callPermButton.text = "✓ 3. Call Filter (Granted)"
                callPermButton.setTextColor(Color.parseColor("#10b981"))
            } else {
                callPermButton.text = "3. Emergency Call Filter (Optional)"
                callPermButton.setTextColor(Color.WHITE)
            }
        }

        if (canDraw) {
            statusText.text = "⚡ Ready! Connect or start session."
            statusText.setTextColor(Color.parseColor("#10b981"))
        } else {
            statusText.text = "Grant Screen Shield permission above to enable"
            statusText.setTextColor(Color.parseColor("#f59e0b"))
        }
    }
}
