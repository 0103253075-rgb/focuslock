package com.focuslock

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class EmergencyPinActivity : Activity() {

    private val MASTER_PIN = "884422"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#18181b"))
            setPadding(50, 50, 50, 50)
        }

        val title = TextView(this).apply {
            text = "Emergency Master Unlock"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 10)
        }

        val subtitle = TextView(this).apply {
            text = "Enter 6-digit Emergency PIN to force unlock phone without laptop:"
            textSize = 12f
            setTextColor(Color.parseColor("#a1a1aa"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }

        val pinInput = EditText(this).apply {
            hint = "Enter PIN (Default: 884422)"
            setHintTextColor(Color.parseColor("#52525b"))
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            textSize = 20f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#27272a"))
            setPadding(30, 30, 30, 30)
        }

        val unlockButton = Button(this).apply {
            text = "UNLOCK PHONE NOW"
            setBackgroundColor(Color.parseColor("#06b6d4"))
            setTextColor(Color.parseColor("#09090b"))
            typeface = Typeface.DEFAULT_BOLD
            textSize = 14f
            setPadding(0, 25, 0, 25)

            setOnClickListener {
                val entered = pinInput.text.toString().trim()
                if (entered == MASTER_PIN) {
                    // Send intent to dismiss overlay
                    val unlockIntent = Intent(this@EmergencyPinActivity, LockOverlayService::class.java).apply {
                        action = LockOverlayService.ACTION_UNLOCK
                    }
                    startService(unlockIntent)

                    Toast.makeText(this@EmergencyPinActivity, "Master PIN Accepted. Unlocked!", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    Toast.makeText(this@EmergencyPinActivity, "Incorrect PIN. Default is 884422", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val cancelButton = Button(this).apply {
            text = "Back to Focus Session"
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.parseColor("#71717a"))
            textSize = 12f
            setOnClickListener { finish() }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(pinInput)
        root.addView(unlockButton)
        root.addView(cancelButton)

        setContentView(root)
    }
}
