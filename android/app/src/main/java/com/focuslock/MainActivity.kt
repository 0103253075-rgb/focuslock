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
    private var selectedDuration = 45

    // Duration buttons
    private lateinit var btn25: Button
    private lateinit var btn45: Button
    private lateinit var btn60: Button
    private lateinit var startLockdownButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isFollowerEdition = BuildConfig.FLAVOR == "follower"

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
            text = if (isFollowerEdition) "Discipline & Deep Work Studio" else "Total Distraction Elimination Studio (Pro)"
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

        // Section 2: Choose Duration
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

        btn25 = createDurationButton("25m\nPomodoro", 25)
        btn45 = createDurationButton("45m\nDeep Work", 45)
        btn60 = createDurationButton("60m\nUltra", 60)

        durationRow.addView(btn25)
        durationRow.addView(btn45)
        durationRow.addView(btn60)

        // Primary Start Lockdown Button
        startLockdownButton = Button(this).apply {
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

        root.addView(logo)
        root.addView(subtitle)
        root.addView(statusText)
        root.addView(permTitle)
        root.addView(overlayButton, marginParams)
        root.addView(dndButton, marginParams)
        root.addView(callPermButton, marginParams)
        root.addView(durationTitle)
        root.addView(durationRow)
        root.addView(startLockdownButton, marginParams)

        // Creator Pro Edition: Wireless Laptop Sync section
        if (!isFollowerEdition) {
            val syncDivider = TextView(this).apply {
                text = "─── OR WIRELESS LAPTOP SYNC ───"
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#52525b"))
                gravity = Gravity.CENTER
                setPadding(0, 25, 0, 15)
            }

            val ipInput = EditText(this).apply {
                hint = "Laptop IP: 192.168.29.197"
                setText("192.168.29.197")
                setHintTextColor(Color.parseColor("#52525b"))
                setTextColor(Color.WHITE)
                textSize = 13f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#18181b"))
                setPadding(20, 20, 20, 20)
            }

            val connectButton = Button(this).apply {
                text = "Connect Wireless Sync"
                setBackgroundColor(Color.parseColor("#27272a"))
                setTextColor(Color.parseColor("#00F0FF"))
                typeface = Typeface.DEFAULT_BOLD
                textSize = 13f
                setOnClickListener {
                    val ip = ipInput.text.toString().trim()
                    val serviceIntent = Intent(this@MainActivity, FocusSyncService::class.java).apply {
                        if (ip.isNotEmpty()) {
                            putExtra("server_ip", ip)
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                    statusText.text = "🟢 Wireless Focus Sync Active"
                    statusText.setTextColor(Color.parseColor("#10b981"))
                    Toast.makeText(this@MainActivity, "Sync running! Ready for laptop lockdown.", Toast.LENGTH_LONG).show()
                }
            }

            root.addView(syncDivider)
            root.addView(ipInput, marginParams)
            root.addView(connectButton, marginParams)
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
        selectDuration(45)
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

        btn25.setBackgroundColor(if (mins == 25) activeBg else inactiveBg)
        btn25.setTextColor(if (mins == 25) activeText else inactiveText)

        btn45.setBackgroundColor(if (mins == 45) activeBg else inactiveBg)
        btn45.setTextColor(if (mins == 45) activeText else inactiveText)

        btn60.setBackgroundColor(if (mins == 60) activeBg else inactiveBg)
        btn60.setTextColor(if (mins == 60) activeText else inactiveText)

        if (::startLockdownButton.isInitialized) {
            startLockdownButton.text = "⚡ START FOCUS LOCKDOWN (${mins}m)"
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

        val lockIntent = Intent(this, LockOverlayService::class.java).apply {
            putExtra(LockOverlayService.EXTRA_DURATION_MINUTES, selectedDuration)
            putExtra(LockOverlayService.EXTRA_REASON, "Follower Focus")
        }
        startService(lockIntent)
        Toast.makeText(this, "Lockdown started for ${selectedDuration} min! Stay disciplined.", Toast.LENGTH_SHORT).show()
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
            statusText.text = "⚡ Ready to Lock! Choose duration below."
            statusText.setTextColor(Color.parseColor("#10b981"))
        } else {
            statusText.text = "Grant Screen Shield permission above to enable"
            statusText.setTextColor(Color.parseColor("#f59e0b"))
        }
    }
}
