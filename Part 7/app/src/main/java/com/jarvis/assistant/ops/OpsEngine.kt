package com.jarvis.assistant.ops

import android.content.Context
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.ops.db.RunEntity
import com.jarvis.assistant.ops.skills.SkillContext
import com.jarvis.assistant.ops.skills.SkillManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Global emergency stop. When engaged, the runner refuses to start and aborts mid-run. */
object EmergencyStop {
    fun isEngaged(context: Context): Boolean = OpsPreferences(context).emergencyStop
    fun engage(context: Context) { OpsPreferences(context).emergencyStop = true }
    fun release(context: Context) { OpsPreferences(context).emergencyStop = false }
}

/** Builds and writes local (on-device) reports. */
object ReportGenerator {
    private val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun build(runs: List<RunEntity>): String {
        val sb = StringBuilder()
        sb.appendLine("JARVIS Ops — Daily Report")
        sb.appendLine("Generated: ${LocalDate.now(ZoneId.of("Asia/Kolkata"))}")
        sb.appendLine("Runs recorded: ${runs.size}")
        sb.appendLine("──────────────────────────────────────────")
        runs.forEach { r ->
            val finished = r.finishedAt?.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.of("Asia/Kolkata")).format(fmt) } ?: "—"
            sb.appendLine("[${r.status}] ${r.slotLabel} • ${r.trigger} • attempts=${r.attempts} • finished=$finished")
            r.summary?.let { sb.appendLine("    summary: $it") }
            r.error?.let { sb.appendLine("    error: $it") }
        }
        return sb.toString()
    }

    fun writeToFile(context: Context, report: String): File {
        val dir = File(context.filesDir, "reports").apply { mkdirs() }
        val name = "ops-report-${LocalDate.now(ZoneId.of("Asia/Kolkata"))}.txt"
        val file = File(dir, name)
        file.writeText(report)
        return file
    }
}

/**
 * The autonomous run orchestrator.
 *
 * For one run it walks the skill pipeline in order, applying:
 *  - bounded retries per skill,
 *  - verification logging for important actions,
 *  - a duplicate-post guard (via the repository),
 *  - a human-approval gate when a skill is uncertain,
 *  - the emergency stop (checked before and between skills).
 */
object OpsEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val IST = ZoneId.of("Asia/Kolkata")

    /** Fire-and-forget entry point used by the scheduler/service. */
    fun launch(context: Context, slot: Slot, trigger: String) {
        val appContext = context.applicationContext
        scope.launch { execute(appContext, slot, trigger) }
    }

    suspend fun execute(context: Context, slot: Slot, trigger: String) = mutex.withLock {
        val app = context.applicationContext as JarvisApp
        val prefs = app.opsPreferences
        val repo = app.opsRepository

        val today = LocalDate.now(IST).toString()
        val postKey = "${slot.display}:$today"

        if (prefs.emergencyStop) {
            return@withLock
        }
        // Duplicate-post guard: a scheduled slot never runs twice for the same day.
        if (trigger != "MANUAL" && repo.hasPosted(postKey)) {
            return@withLock
        }

        val runId = repo.startRun(slot.label, trigger, postKey)
        repo.log(runId, LogLevel.ACTION, "Run started (trigger=$trigger, slot=${slot.display} IST)")

        val pipeline = SkillManager.pipelineOrder
        var attemptsTotal = 0
        var notImplemented = 0

        for (skillId in pipeline) {
            if (prefs.emergencyStop) {
                repo.log(runId, LogLevel.WARN, "Emergency stop engaged — aborting run")
                repo.finishRun(runId, RunStatus.CANCELLED, "Emergency stop engaged", null)
                return@withLock
            }

            val skill = SkillManager.get(skillId) ?: continue
            val max = prefs.maxRetries
            var attempt = 0
            var outcome: SkillOutcome? = null

            while (attempt < max) {
                attempt++
                attemptsTotal++
                outcome = try {
                    skill.execute(SkillContext(runId, slot.label, attempt, repo, context, prefs))
                } catch (e: Exception) {
                    SkillOutcome.failed("${e.javaClass.simpleName}: ${e.message}")
                }
                repo.log(runId, LogLevel.INFO, "${skillId.title}: attempt $attempt → ${outcome.status}")
                if (outcome.status != SkillStatus.FAILED) break
                delay(1000L * attempt)
            }
            repo.setAttempts(runId, attemptsTotal)

            when (outcome?.status) {
                SkillStatus.SUCCESS -> {
                    repo.log(runId, LogLevel.VERIFY, "${skillId.title} verified: ${outcome.verificationNote ?: outcome.message}")
                    if (skillId == SkillId.INSTAGRAM_PUBLISH) {
                        repo.recordPost(postKey, slot.label, outcome.message)
                        repo.log(runId, LogLevel.ACTION, "Post recorded for dedupe key $postKey")
                    }
                }
                SkillStatus.SKIPPED -> repo.log(runId, LogLevel.INFO, "${skillId.title} skipped: ${outcome.message}")
                SkillStatus.NOT_IMPLEMENTED -> {
                    notImplemented++
                    repo.log(runId, LogLevel.WARN, "${skillId.title} NOT IMPLEMENTED: ${outcome.message}")
                }
                SkillStatus.NEEDS_APPROVAL -> {
                    repo.requestApproval(runId, outcome.message, "Skill: ${skillId.title}")
                    repo.log(runId, LogLevel.WARN, "${skillId.title} needs approval — run paused")
                    repo.finishRun(runId, RunStatus.NEEDS_APPROVAL, null, null)
                    return@withLock
                }
                SkillStatus.BLOCKED -> {
                    repo.finishRun(runId, RunStatus.BLOCKED, null, "${skillId.title}: ${outcome.message}")
                    return@withLock
                }
                SkillStatus.FAILED -> {
                    repo.finishRun(runId, RunStatus.FAILED, null, "${skillId.title} failed after $max attempts: ${outcome.message}")
                    return@withLock
                }
                null -> Unit
            }
        }

        repo.finishRun(
            runId,
            RunStatus.SUCCESS,
            "Pipeline completed (${pipeline.size} skills, $attemptsTotal attempts, $notImplemented not implemented)",
            null
        )
    }
}
