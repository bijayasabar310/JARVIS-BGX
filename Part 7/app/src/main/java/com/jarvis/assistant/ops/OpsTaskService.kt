package com.jarvis.assistant.ops

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.jarvis.assistant.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * Foreground service that runs one autonomous task so long work is not killed when the
 * alarm receiver returns. Re-arms the next slot when finished.
 */
class OpsTaskService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val trigger = intent?.getStringExtra(EXTRA_TRIGGER) ?: "SCHEDULED"
        startInForeground(trigger)
        val ctx = applicationContext
        scope.launch {
            try {
                val slot = OpsScheduler.slotAtFire(
                    ZonedDateTime.now(OpsScheduler.IST),
                    OpsPreferences(ctx).slots()
                )
                if (slot != null) {
                    OpsEngine.execute(ctx, slot, trigger)
                }
            } catch (_: Exception) {
                // Never crash the service; the run log already captures skill-level failures.
            } finally {
                OpsScheduler.reschedule(ctx)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(trigger: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "JARVIS Ops", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS Ops")
            .setContentText("Running autonomous task ($trigger)…")
            .setSmallIcon(R.drawable.ic_social_telegram)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notif, type)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_TRIGGER = "trigger"
        private const val CHANNEL_ID = "jarvis_ops"
        private const val NOTIF_ID = 8802

        fun start(context: Context, trigger: String) {
            val i = Intent(context, OpsTaskService::class.java).putExtra(EXTRA_TRIGGER, trigger)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}
