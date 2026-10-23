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
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var overlayButton: Button
    private lateinit var dndButton: Button
    private lateinit var callPermButton: Button
    private lateinit var ipInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#09090b"))
            setPadding(50, 60, 50, 40)
        }

        val logo = TextView(this).apply {
            text = "🛡️ FocusLock"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 10)
        }

        val subtitle = TextView(this).apply {
            text = "Total Distraction Elimination Studio"
            textSize = 13f
            setTextColor(Color.parseColor("#71717a"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }

        statusText = TextView(this).apply {
            text = "Grant permissions below for 100% total silence"
            textSize = 13f
            setTextColor(Color.parseColor("#f59e0b"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 25)
        }

        // Button 1: Overlay Permission
        overlayButton = Button(this).apply {
            text = "1. Screen Shield (Draw Over Apps)"
            setBackgroundColor(Color.parseColor("#27272a"))
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

        // Button 2: Do Not Disturb Silence Permission
        dndButton = Button(this).apply {
            text = "2. Total Silence (Do Not Disturb Access)"
            setBackgroundColor(Color.parseColor("#27272a"))
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

        // Button 3: Call Screening Permission
        callPermButton = Button(this).apply {
            text = "3. Call Filter (Mute 1st Call / Ring 2nd)"
            setBackgroundColor(Color.parseColor("#27272a"))
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

        // Input: Laptop IP Address
        ipInput = EditText(this).apply {
            hint = "192.168.29.197 or leave blank"
            setText("192.168.29.197")
            setHintTextColor(Color.parseColor("#52525b"))
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#18181b"))
            setPadding(25, 25, 25, 25)
        }

        // Button 4: Connect Service
        val connectButton = Button(this).apply {
            text = "4. Connect Wireless Sync"
            setBackgroundColor(Color.parseColor("#06b6d4"))
            setTextColor(Color.parseColor("#09090b"))
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

        val safetyNotice = TextView(this).apply {
            text = "🔒 Safety Guarantee:\n- Master PIN: 884422 (tap screen 5 times to reveal)\n- Auto-unlocks when timer reaches 00:00\n- Emergency callers calling twice will ring through"
            textSize = 11f
            setTextColor(Color.parseColor("#52525b"))
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 0)
            setLineSpacing(4f, 1f)
        }

        val marginParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, 16)
        }

        root.addView(logo)
        root.addView(subtitle)
        root.addView(statusText)
        root.addView(overlayButton, marginParams)
        root.addView(dndButton, marginParams)
        root.addView(callPermButton, marginParams)
        root.addView(ipInput, marginParams)
        root.addView(connectButton, marginParams)
        root.addView(safetyNotice)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()
    }

    private fun updatePermissionStates() {
        var allReady = true

        // Check Overlay
        val canDraw = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        if (canDraw) {
            overlayButton.text = "✓ 1. Screen Shield (Granted)"
            overlayButton.setTextColor(Color.parseColor("#10b981"))
        } else {
            allReady = false
        }

        // Check DND
        var canDnd = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            canDnd = nm.isNotificationPolicyAccessGranted
            if (canDnd) {
                dndButton.text = "✓ 2. Total Silence (Granted)"
                dndButton.setTextColor(Color.parseColor("#10b981"))
            } else {
                allReady = false
            }
        }

        // Check Call Perms
        var canCall = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val statePerm = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            canCall = statePerm
            if (canCall) {
                callPermButton.text = "✓ 3. Call Filter (Granted)"
                callPermButton.setTextColor(Color.parseColor("#10b981"))
            } else {
                allReady = false
            }
        }

        if (allReady) {
            statusText.text = "⚡ All Protections Active! Tap Connect below."
            statusText.setTextColor(Color.parseColor("#10b981"))
        }
    }
}
