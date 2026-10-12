package com.jarvis.assistant.ops

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires at a scheduled slot: starts the task service, then re-arms the next slot. */
class OpsAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = OpsPreferences(context)
        if (!prefs.enabled || prefs.emergencyStop) {
            OpsScheduler.reschedule(context)
            return
        }
        OpsTaskService.start(context, "SCHEDULED")
        OpsScheduler.reschedule(context)
    }
}

/** Re-arms the schedule after a reboot. */
class OpsBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            OpsScheduler.reschedule(context)
        }
    }
}
