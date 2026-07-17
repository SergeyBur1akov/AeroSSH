package com.companyname.aerossh.security

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.companyname.aerossh.LockActivity

object VaultLockManager {
    private const val TIMEOUT_MS = 60_000L
    private val handler = Handler(Looper.getMainLooper())
    private var lockedRunnable: Runnable? = null
    private var lastResumed = 0L
    private var activityCount = 0

    fun onActivityResumed(activity: Activity) {
        if (activity is LockActivity) return
        activityCount++
        lastResumed = System.currentTimeMillis()
        cancelPendingLock()
        if (!LuksEncryption.isVaultUnlocked()) {
            activity.startActivity(Intent(activity, LockActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            activity.finish()
        }
    }

    fun onActivityStopped(activity: Activity) {
        if (activity is LockActivity) return
        activityCount--
        if (activityCount <= 0) {
            activityCount = 0
            scheduleLock()
        }
    }

    private fun scheduleLock() {
        cancelPendingLock()
        lockedRunnable = Runnable { lockVault() }
        handler.postDelayed(lockedRunnable!!, TIMEOUT_MS)
    }

    private fun cancelPendingLock() {
        lockedRunnable?.let { handler.removeCallbacks(it) }
        lockedRunnable = null
    }

    private fun lockVault() {
        if (LuksEncryption.isVaultUnlocked()) {
            LuksEncryption.lockVault()
        }
    }
}
