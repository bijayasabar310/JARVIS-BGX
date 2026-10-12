package com.jarvis.assistant.ops

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Persistent daily-slot scheduler (AlarmManager based).
 *
 * Slots are IST times (default 08:30, 13:00, 19:00). Scheduling survives reboots via
 * [OpsBootReceiver] and is re-armed after every fire.
 */
object OpsScheduler {

    val IST: ZoneId = ZoneId.of("Asia/Kolkata")
    private const val REQUEST_CODE = 8801
    private const val ACTION = "com.jarvis.assistant.ops.SLOT"

    /** Next (slot, time) strictly after [now], searching up to 8 days ahead. */
    fun nextSlotAfter(now: ZonedDateTime, slots: List<Slot>): Pair<Slot, ZonedDateTime>? {
        if (slots.isEmpty()) return null
        val base = now.toLocalDate()
        for (day in 0..8) {
            val date = base.plusDays(day.toLong())
            for (s in slots) {
                val t = ZonedDateTime.of(date, LocalTime.of(s.hour, s.minute), IST)
                if (t.isAfter(now)) return s to t
            }
        }
        return null
    }

    /** The slot whose time most recently passed today (used when an alarm actually fires). */
    fun slotAtFire(now: ZonedDateTime, slots: List<Slot>): Slot? {
        if (slots.isEmpty()) return null
        val minutesNow = now.hour * 60 + now.minute
        return slots.lastOrNull { it.hour * 60 + it.minute <= minutesNow } ?: slots.first()
    }

    fun reschedule(context: Context) {
        val prefs = OpsPreferences(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context)
        am.cancel(pi)
        if (!prefs.enabled || prefs.emergencyStop) return

        val next = nextSlotAfter(ZonedDateTime.now(IST), prefs.slots()) ?: return
        val millis = next.second.toInstant().toEpochMilli()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, OpsAlarmReceiver::class.java).setAction(ACTION)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
