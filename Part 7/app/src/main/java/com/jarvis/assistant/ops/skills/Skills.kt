package com.jarvis.assistant.ops.skills

import android.content.Context
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.ops.OpsPreferences
import com.jarvis.assistant.ops.OpsRepository
import com.jarvis.assistant.ops.ReportGenerator
import com.jarvis.assistant.ops.SkillId
import com.jarvis.assistant.ops.SkillOutcome
import com.jarvis.assistant.ops.video.LocalVideoEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/** Everything a skill needs for one execution attempt. */
data class SkillContext(
    val runId: Long,
    val slotLabel: String,
    val attempt: Int,
    val repo: OpsRepository,
    val appContext: Context,
    val prefs: OpsPreferences
)

/**
 * A modular, independently retryable unit of autonomous work.
 *
 * Skills must be honest: if a capability is not implemented, return
 * [com.jarvis.assistant.ops.SkillStatus.NOT_IMPLEMENTED] rather than pretending to succeed.
 */
interface Skill {
    val id: SkillId
    val description: String
    suspend fun execute(ctx: SkillContext): SkillOutcome
}

// ─────────────────────────────────────────────────────────────────────────────
// Real skills (implemented and verifiable on-device)
// ─────────────────────────────────────────────────────────────────────────────

/** Writes a local daily report of scheduler activity. Local only, no network. */
object DailyReportSkill : Skill {
    override val id = SkillId.DAILY_REPORT
    override val description = "Write a local daily report of scheduler activity and outcomes."

    override suspend fun execute(ctx: SkillContext): SkillOutcome {
        val runs = ctx.repo.runsOnce(20)
        val report = ReportGenerator.build(runs)
        ReportGenerator.writeToFile(ctx.appContext, report)
        return SkillOutcome.success(
            "Daily report written (${runs.size} recent runs).",
            mapOf("reportPreview" to report.take(400)),
            verified = "Report file written to app filesDir/reports"
        )
    }
}

/** Generates an Instagram caption + hashtags with Gemini. Requires a configured API key. */
object CaptionHashtagSkill : Skill {
    override val id = SkillId.CAPTION_HASHTAGS
    override val description = "Generate an Instagram caption + hashtags with Gemini."

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * The app's default model is a Live-API model that only supports bidiGenerateContent over
     * WebSocket, so it cannot serve a plain REST generateContent call. Try generateContent-capable
     * Flash models instead, in order, until one succeeds.
     */
    private val candidateModels = listOf(
        "models/gemini-2.5-flash",
        "models/gemini-2.0-flash",
        "models/gemini-flash-latest",
        "models/gemini-1.5-flash"
    )

    override suspend fun execute(ctx: SkillContext): SkillOutcome {
        val key = JarvisApp.instance.preferences.apiKey
        if (key.isBlank()) {
            return SkillOutcome.blocked("No Gemini API key configured (Settings → API connection).")
        }

        val preferred = ctx.prefs.captionModel.trim()
        val models = ((if (preferred.isNotBlank()) listOf(preferred) else emptyList()) + candidateModels).distinct()

        val prompt = "Write one short, engaging Instagram caption (max 2 lines) and 12 relevant hashtags " +
                "for an AI-assistant / JARVIS themed short video. Reply as plain text: the caption, then a " +
                "line 'HASHTAGS:' followed by the hashtags separated by spaces."

        return withContext(Dispatchers.IO) {
            var lastError = "no model attempted"
            for (model in models) {
                try {
                    val body = """{"contents":[{"parts":[{"text":${JsonPrimitive(prompt)}}]}]}"""
                    val request = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/$model:generateContent?key=$key")
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .build()
                    client.newCall(request).execute().use { resp ->
                        val text = resp.body?.string().orEmpty()
                        if (resp.isSuccessful) {
                            val parsed = JsonParser.parseString(text).asJsonObject
                            val candidate = parsed.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject
                            val parts = candidate?.getAsJsonObject("content")?.getAsJsonArray("parts")
                            val out = parts?.firstOrNull()?.asJsonObject?.get("text")?.asString
                            if (!out.isNullOrBlank()) {
                                return@withContext SkillOutcome.success(
                                    "Caption + hashtags generated with $model.",
                                    mapOf("caption" to out, "model" to model),
                                    verified = "Gemini returned text via generateContent ($model)"
                                )
                            }
                            lastError = "empty response from $model"
                        } else {
                            lastError = "HTTP ${resp.code} from $model: ${text.take(160)}"
                        }
                    }
                } catch (e: Exception) {
                    lastError = "error with $model: ${e.message}"
                }
            }
            SkillOutcome.failed("Caption generation failed for all candidate models. Last error: $lastError")
        }
    }
}

/** Records the configured post-publish follow-up delay (scheduling is driven by the runner). */
object FollowUpSkill : Skill {
    override val id = SkillId.POST_FOLLOW_UP
    override val description = "Schedule a one-hour (configurable) post-publish follow-up."

    override suspend fun execute(ctx: SkillContext): SkillOutcome {
        val minutes = ctx.prefs.followUpDelayMinutes
        return SkillOutcome.success(
            "Follow-up delay set to $minutes min (applied after a verified publish).",
            mapOf("followUpMinutes" to minutes.toString()),
            verified = "Follow-up delay read from preferences"
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Declared-but-not-yet-implemented skills.
// These are deliberately honest: they report NOT_IMPLEMENTED rather than faking success.
// ─────────────────────────────────────────────────────────────────────────────

/** Planned: drive Chrome to Google Flow via the existing signed-in session (no credentials stored). */
object FlowVideoSkill : Skill {
    override val id = SkillId.FLOW_VIDEO
    override val description = "Generate two video clips via Google Flow in Chrome, then select the best."

    override suspend fun execute(ctx: SkillContext): SkillOutcome =
        SkillOutcome.notImplemented(
            "Not implemented yet. Planned: open Google Flow in Chrome using the existing signed-in session " +
                    "and drive it with JarvisAccessibilityService + AgentTaskEngine (no credentials stored), " +
                    "generate two clips per task, and verify each render before continuing. This needs a " +
                    "validated Flow UI flow and is the next increment."
        )
}

/** Combines local clips, burns the authorized logo, preserves original audio, exports an MP4. */
object VideoEditSkill : Skill {
    override val id = SkillId.VIDEO_EDIT
    override val description = "Combine clips locally, add the authorized logo, keep original audio, export."

    override suspend fun execute(ctx: SkillContext): SkillOutcome {
        val clips = gatherClips(ctx)
        if (clips.isEmpty()) {
            return SkillOutcome.blocked(
                "No input clips found. Put video files in ${defaultClipsDir(ctx.appContext).absolutePath} " +
                        "(or generate them with Flow), then run again."
            )
        }

        val outDir = File(baseDir(ctx.appContext), "exports").apply { mkdirs() }
        val output = File(outDir, "export-${System.currentTimeMillis()}.mp4")
        val logoPath = resolveLogo(ctx)

        val result = LocalVideoEditor.export(ctx.appContext, clips, output, logoPath)
        if (!result.ok) return SkillOutcome.failed(result.message)

        val durationMs = LocalVideoEditor.probeDurationMs(output)
        val (w, h) = LocalVideoEditor.probeWidthHeight(output)
        if (durationMs <= 0 || output.length() <= 0) {
            return SkillOutcome.failed("Export produced an unreadable file (${output.name}).")
        }

        val musicNote = if (ctx.prefs.musicPath.isNotBlank()) " (music mixing not applied yet)" else ""
        return SkillOutcome.success(
            "Combined ${clips.size} clip(s)${if (logoPath != null) " with logo" else ""}; " +
                    "exported ${output.name} (${durationMs / 1000}s, ${w}x$h)$musicNote.",
            mapOf(
                "output" to output.absolutePath,
                "clips" to clips.size.toString(),
                "logo" to (logoPath ?: "none")
            ),
            verified = "Output verified on-device: ${durationMs}ms, ${w}x$h, ${output.length()} bytes"
        )
    }

    /** App-specific external dir (file-manager accessible) so the user can drop clips in. */
    private fun baseDir(context: android.content.Context): File =
        context.getExternalFilesDir(null) ?: context.filesDir

    /**
     * Logo to burn in. Uses the configured path if set, otherwise auto-detects a
     * logo.png/jpg/jpeg/webp placed next to the clips folder.
     */
    private fun resolveLogo(ctx: SkillContext): String? {
        val configured = ctx.prefs.logoPath.trim()
        if (configured.isNotBlank() && File(configured).exists()) return configured
        val base = baseDir(ctx.appContext)
        for (name in listOf("logo.png", "logo.jpg", "logo.jpeg", "logo.webp")) {
            val f = File(base, name)
            if (f.exists()) return f.absolutePath
        }
        return null
    }

    private fun defaultClipsDir(context: android.content.Context) = File(baseDir(context), "clips")

    private fun gatherClips(ctx: SkillContext): List<File> {
        val configured = ctx.prefs.clipsDir.trim()
        val dir = if (configured.isNotBlank()) File(configured) else defaultClipsDir(ctx.appContext)
        if (!dir.exists()) return emptyList()
        val exts = setOf("mp4", "mov", "mkv", "webm", "m4v", "3gp")
        return (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.extension.lowercase() in exts && it.length() > 0 }
            .sortedBy { it.name }
            .take(4)
    }
}

/** Planned: publish via the existing logged-in Instagram UI, with approval + duplicate-post guard. */
object InstagramPublishSkill : Skill {
    override val id = SkillId.INSTAGRAM_PUBLISH
    override val description = "Publish to Instagram via the existing logged-in UI."

    override suspend fun execute(ctx: SkillContext): SkillOutcome =
        SkillOutcome.notImplemented(
            "Not implemented yet. Planned: publish through the already-logged-in Instagram UI using " +
                    "AccessibilityService automation, requesting human approval when the target account or " +
                    "the initial comment reply is uncertain, verifying the post, and enforcing a duplicate-post " +
                    "guard. No passwords or tokens are stored. Note: UI automation of Instagram is outside its " +
                    "terms of service and can put the account at risk."
        )
}

/**
 * Registry for all skills. Add a new capability by implementing [Skill] and registering it here.
 */
object SkillManager {
    private val registry = LinkedHashMap<SkillId, Skill>()

    /** Pipeline order for a scheduled run. */
    val pipelineOrder: List<SkillId> = listOf(
        SkillId.FLOW_VIDEO,
        SkillId.VIDEO_EDIT,
        SkillId.CAPTION_HASHTAGS,
        SkillId.INSTAGRAM_PUBLISH,
        SkillId.POST_FOLLOW_UP
    )

    fun register(skill: Skill) {
        registry[skill.id] = skill
    }

    fun get(id: SkillId): Skill? = registry[id]

    fun all(): List<Skill> = registry.values.toList()

    fun registerDefaults() {
        register(FlowVideoSkill)
        register(VideoEditSkill)
        register(CaptionHashtagSkill)
        register(InstagramPublishSkill)
        register(FollowUpSkill)
        register(DailyReportSkill)
    }
}
