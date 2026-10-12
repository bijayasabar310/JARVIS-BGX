package com.jarvis.assistant.ops

import com.jarvis.assistant.ops.db.ApprovalEntity
import com.jarvis.assistant.ops.db.LogEntity
import com.jarvis.assistant.ops.db.OpsDao
import com.jarvis.assistant.ops.db.PostEntity
import com.jarvis.assistant.ops.db.RunEntity
import kotlinx.coroutines.flow.Flow

/**
 * Facade over the local ops database: runs, logs, published-post records and approvals.
 * Everything is stored on-device only.
 */
class OpsRepository(private val dao: OpsDao) {

    val recentRuns: Flow<List<RunEntity>> = dao.runs(50)
    val recentLogs: Flow<List<LogEntity>> = dao.recentLogs(200)
    val pendingApprovals: Flow<List<ApprovalEntity>> = dao.pendingApprovals()

    suspend fun startRun(slotLabel: String, trigger: String, dedupeKey: String): Long =
        dao.insertRun(
            RunEntity(
                slotLabel = slotLabel,
                trigger = trigger,
                dedupeKey = dedupeKey,
                status = RunStatus.RUNNING.name,
                startedAt = System.currentTimeMillis()
            )
        )

    suspend fun finishRun(id: Long, status: RunStatus, summary: String?, error: String?) {
        val r = dao.run(id) ?: return
        dao.updateRun(
            r.copy(
                status = status.name,
                finishedAt = System.currentTimeMillis(),
                summary = summary,
                error = error
            )
        )
    }

    suspend fun setAttempts(id: Long, attempts: Int) {
        val r = dao.run(id) ?: return
        dao.updateRun(r.copy(attempts = attempts))
    }

    suspend fun log(runId: Long, level: LogLevel, message: String) =
        dao.insertLog(LogEntity(runId = runId, level = level.name, message = message, at = System.currentTimeMillis()))

    /** Duplicate-post guard: true if this dedupe key has already been published. */
    suspend fun hasPosted(dedupeKey: String): Boolean = dao.postCount(dedupeKey) > 0

    suspend fun recordPost(dedupeKey: String, slotLabel: String, detail: String) =
        dao.insertPost(PostEntity(dedupeKey = dedupeKey, slotLabel = slotLabel, postedAt = System.currentTimeMillis(), detail = detail))

    suspend fun runsOnce(limit: Int = 50): List<RunEntity> = dao.runsOnce(limit)
    suspend fun logsOnce(runId: Long): List<LogEntity> = dao.logsOnce(runId)

    suspend fun requestApproval(runId: Long, question: String, detail: String): Long =
        dao.insertApproval(ApprovalEntity(runId = runId, question = question, detail = detail, createdAt = System.currentTimeMillis()))

    suspend fun resolveApproval(id: Long, approved: Boolean) {
        val a = dao.approval(id) ?: return
        dao.updateApproval(a.copy(resolved = true, approved = approved))
    }
}
