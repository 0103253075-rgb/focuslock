package com.focuslock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Smart Emergency Call Filter:
 * - When FocusLock is active:
 *   - Call #1 from any number: Silenced completely.
 *   - Call #2 from the SAME number within 5 minutes: Rings out loud (Emergency bypass).
 */
class EmergencyCallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "FocusLock_Call"
        private const val EMERGENCY_WINDOW_MS = 5 * 60 * 1000L // 5 minutes window

        // In-memory cache of recent callers and their timestamps
        private val recentCallers = HashMap<String, Long>()

        var isFocusLockActive: Boolean = false
        var previousRingerVolume: Int = -1
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!isFocusLockActive) return

        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: "Private Number"
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    val currentTime = System.currentTimeMillis()
                    val lastCallTime = recentCallers[incomingNumber] ?: 0L
                    val isRepeatCall = (currentTime - lastCallTime) < EMERGENCY_WINDOW_MS

                    if (isRepeatCall) {
                        // 🚨 EMERGENCY BYPASS: Called twice within 5 mins!
                        Log.i(TAG, "EMERGENCY: Repeat call from $incomingNumber. Unmuting ringer aloud!")
                        
                        // Restore ringer to audible volume
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                        audioManager.setStreamVolume(AudioManager.STREAM_RING, maxVol, 0)
                        
                        // Notify laptop dashboard
                        FocusSyncService.sendCallEvent(incomingNumber, isRepeatCaller = true, action = "RING_ALLOWED")
                    } else {
                        // 🔕 FIRST ATTEMPT: Mute ringer silently
                        Log.i(TAG, "Muting first call attempt from $incomingNumber")
                        if (previousRingerVolume == -1) {
                            previousRingerVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                        }
                        
                        // Mute ringer
                        audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
                        
                        // Record this caller's timestamp
                        recentCallers[incomingNumber] = currentTime
                        
                        // Notify laptop dashboard
                        FocusSyncService.sendCallEvent(incomingNumber, isRepeatCaller = false, action = "MUTED")
                    }
                }

                TelephonyManager.EXTRA_STATE_IDLE -> {
                    // Call ended
                    Log.d(TAG, "Call ended or dismissed.")
                }
            }
        }
    }
}
