package com.jarvis.assistant.ops.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** One execution of the autonomous pipeline (scheduled, manual, or follow-up). */
@Entity(tableName = "ops_run")
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slotLabel: String,
    val trigger: String,
    val dedupeKey: String,
    val status: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val attempts: Int = 0,
    val summary: String? = null,
    val error: String? = null
)

/** A single line in the local run log. */
@Entity(tableName = "ops_log")
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val level: String,
    val message: String,
    val at: Long
)

/** Records a verified publish so the same slot/date is never posted twice. */
@Entity(tableName = "ops_post")
data class PostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dedupeKey: String,
    val slotLabel: String,
    val postedAt: Long,
    val detail: String
)

/** A pending human-approval request (e.g. uncertain Instagram account selection). */
@Entity(tableName = "ops_approval")
data class ApprovalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val question: String,
    val detail: String,
    val createdAt: Long,
    val resolved: Boolean = false,
    val approved: Boolean = false
)

@Dao
interface OpsDao {
    @Insert suspend fun insertRun(r: RunEntity): Long
    @Update suspend fun updateRun(r: RunEntity)
    @Query("SELECT * FROM ops_run WHERE id = :id") suspend fun run(id: Long): RunEntity?
    @Query("SELECT * FROM ops_run ORDER BY startedAt DESC LIMIT :limit") fun runs(limit: Int): Flow<List<RunEntity>>
    @Query("SELECT * FROM ops_run ORDER BY startedAt DESC LIMIT :limit") suspend fun runsOnce(limit: Int): List<RunEntity>

    @Insert suspend fun insertLog(l: LogEntity)
    @Query("SELECT * FROM ops_log ORDER BY at DESC LIMIT :limit") fun recentLogs(limit: Int): Flow<List<LogEntity>>
    @Query("SELECT * FROM ops_log WHERE runId = :runId ORDER BY at ASC") suspend fun logsOnce(runId: Long): List<LogEntity>

    @Insert suspend fun insertPost(p: PostEntity)
    @Query("SELECT COUNT(*) FROM ops_post WHERE dedupeKey = :key") suspend fun postCount(key: String): Int

    @Insert suspend fun insertApproval(a: ApprovalEntity): Long
    @Query("SELECT * FROM ops_approval WHERE resolved = 0 ORDER BY createdAt DESC") fun pendingApprovals(): Flow<List<ApprovalEntity>>
    @Query("SELECT * FROM ops_approval WHERE id = :id") suspend fun approval(id: Long): ApprovalEntity?
    @Update suspend fun updateApproval(a: ApprovalEntity)
}

@Database(
    entities = [RunEntity::class, LogEntity::class, PostEntity::class, ApprovalEntity::class],
    version = 1,
    exportSchema = false
)
abstract class OpsDatabase : RoomDatabase() {

    abstract fun opsDao(): OpsDao

    companion object {
        private const val DB_NAME = "jarvis_ops.db"

        @Volatile
        private var instance: OpsDatabase? = null

        fun getInstance(context: Context): OpsDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OpsDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
