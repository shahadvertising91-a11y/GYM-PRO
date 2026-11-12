package com.forge.bar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Brings the notification bar back after a reboot (only if the user had it running). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED) return
        val s = StepStore(c)
        if (!s.running) return
        try {
            ContextCompat.startForegroundService(
                c, Intent(c, StepService::class.java)
                    .putExtra(StepService.EXTRA_GOAL, s.goal).putExtra(StepService.EXTRA_KPS, s.kps)
            )
        } catch (_: Exception) { /* Android may refuse background starts; the bar returns when the app is opened */ }
    }
}
