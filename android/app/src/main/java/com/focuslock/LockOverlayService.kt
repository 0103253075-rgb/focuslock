package com.focuslock

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Service wrapper that delegates to LockOverlayManager.
 */
class LockOverlayService : Service() {

    companion object {
        const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"
        const val EXTRA_REASON = "extra_reason"
        const val MASTER_PIN = LockOverlayManager.MASTER_PIN
        const val ACTION_UNLOCK = "com.focuslock.ACTION_UNLOCK"

        val isOverlayShowing: Boolean
            get() = LockOverlayManager.isOverlayShowing

        fun dismissNow() {
            LockOverlayManager.dismiss()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_UNLOCK) {
            LockOverlayManager.dismiss(this)
            stopSelf()
            return START_NOT_STICKY
        }

        val duration = intent?.getIntExtra(EXTRA_DURATION_MINUTES, 45) ?: 45
        val reason = intent?.getStringExtra(EXTRA_REASON) ?: "Deep Focus"

        LockOverlayManager.show(this, duration, reason)
        return START_STICKY
    }

    override fun onDestroy() {
        LockOverlayManager.dismiss(this)
        super.onDestroy()
    }
}
