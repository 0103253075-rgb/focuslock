package com.focuslock

import android.app.Activity
import android.content.Intent
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
    private lateinit var ipInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#09090b"))
            setPadding(50, 70, 50, 50)
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
            text = "Wireless Anti-Distraction Companion"
            textSize = 13f
            setTextColor(Color.parseColor("#71717a"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }

        statusText = TextView(this).apply {
            text = "Setup Required: Grant Permissions below"
            textSize = 14f
            setTextColor(Color.parseColor("#f59e0b"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }

        // Button 1: Overlay Permission
        val overlayButton = Button(this).apply {
            text = "1. Grant 'Draw Over Other Apps' Permission"
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
                    Toast.makeText(this@MainActivity, "Overlay permission already granted!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Input: Laptop IP Address
        ipInput = EditText(this).apply {
            hint = "192.168.29.197 or leave blank"
            setText("192.168.29.197")
            setHintTextColor(Color.parseColor("#52525b"))
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#18181b"))
            setPadding(30, 30, 30, 30)
        }

        // Button 2: Connect Service
        val connectButton = Button(this).apply {
            text = "2. Start Wireless Sync Service"
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
                statusText.text = "🟢 Wireless Sync Service Running"
                statusText.setTextColor(Color.parseColor("#10b981"))
                Toast.makeText(this@MainActivity, "Sync connected! You can now control phone from laptop.", Toast.LENGTH_LONG).show()
            }
        }

        val safetyNotice = TextView(this).apply {
            text = "🔒 Safety Guarantee:\n- Emergency Master PIN: 884422\n- Independent countdown auto-unlocks when session ends.\n- Calls dialed twice will bypass mute."
            textSize = 11f
            setTextColor(Color.parseColor("#52525b"))
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 0)
            setLineSpacing(5f, 1f)
        }

        root.addView(logo)
        root.addView(subtitle)
        root.addView(statusText)
        root.addView(overlayButton)
        root.addView(ipInput)
        root.addView(connectButton)
        root.addView(safetyNotice)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            statusText.text = "Permissions ready. Enter Laptop IP to sync."
            statusText.setTextColor(Color.parseColor("#a1a1aa"))
        }
    }
}
