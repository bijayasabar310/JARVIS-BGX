package com.jarvis.assistant.ui.dashboard

import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.ops.OpsScheduler
import com.jarvis.assistant.ops.OpsTaskService
import com.jarvis.assistant.ops.skills.SkillManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.time.ZonedDateTime

/**
 * Local dashboard for the autonomous operations subsystem: schedule state, emergency stop,
 * the skill registry, recent runs and recent logs. Read/write is entirely on-device.
 */
class DashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val app = applicationContext as JarvisApp
        val prefs = app.opsPreferences
        val repo = app.opsRepository

        val switchOps = findViewById<Switch>(R.id.switchOps)
        val switchEmergency = findViewById<Switch>(R.id.switchEmergency)
        val tvNext = findViewById<TextView>(R.id.tvNext)
        val tvSkills = findViewById<TextView>(R.id.tvSkills)
        val tvRuns = findViewById<TextView>(R.id.tvRuns)
        val tvLogs = findViewById<TextView>(R.id.tvLogs)
        val btnRunNow = findViewById<Button>(R.id.btnRunNow)
        val btnRefresh = findViewById<Button>(R.id.btnRefresh)

        switchOps.isChecked = prefs.enabled
        switchEmergency.isChecked = prefs.emergencyStop

        fun refreshHeader() {
            val clipsDir = File(applicationContext.getExternalFilesDir(null) ?: filesDir, "clips")
            val scheduleLine = when {
                !prefs.enabled -> "Scheduler: OFF"
                prefs.emergencyStop -> "EMERGENCY STOP engaged — scheduler halted"
                else -> {
                    val next = OpsScheduler.nextSlotAfter(ZonedDateTime.now(OpsScheduler.IST), prefs.slots())
                    val slotList = prefs.slots().joinToString { it.display }
                    if (next == null) "No slots configured (slots: $slotList)"
                    else "Next run: ${next.first.display} IST on ${next.second.toLocalDate()}  •  slots: $slotList"
                }
            }
            tvNext.text = "$scheduleLine\nClips folder: ${clipsDir.absolutePath}"
            tvSkills.text = buildString {
                appendLine("Skills (${SkillManager.all().size}):")
                SkillManager.all().forEach { appendLine("• ${it.id.title} — ${it.description}") }
            }
        }
        refreshHeader()

        switchOps.setOnCheckedChangeListener { _, v ->
            prefs.enabled = v
            OpsScheduler.reschedule(this)
            refreshHeader()
        }
        switchEmergency.setOnCheckedChangeListener { _, v ->
            prefs.emergencyStop = v
            if (v) OpsScheduler.cancel(this)
            OpsScheduler.reschedule(this)
            refreshHeader()
        }
        btnRunNow.setOnClickListener { OpsTaskService.start(this, "MANUAL") }
        btnRefresh.setOnClickListener { refreshHeader() }

        lifecycleScope.launch {
            repo.recentRuns.collectLatest { runs ->
                tvRuns.text = buildString {
                    appendLine("Recent runs (${runs.size}):")
                    runs.forEach { r ->
                        append("[${r.status}] ${r.slotLabel} • ${r.trigger} • attempts=${r.attempts}")
                        r.summary?.let { append(" • $it") }
                        r.error?.let { append(" • ERR: $it") }
                        appendLine()
                    }
                }
            }
        }

        lifecycleScope.launch {
            repo.recentLogs.collectLatest { logs ->
                tvLogs.text = buildString {
                    appendLine("Recent logs (${logs.size}):")
                    logs.take(80).forEach { appendLine("${it.level}  ${it.message}") }
                }
            }
        }
    }

}
