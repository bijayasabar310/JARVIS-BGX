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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    override suspend fun execute(ctx: SkillContext): SkillOutcome {
        val appPrefs = JarvisApp.instance.preferences
        val key = appPrefs.apiKey
        if (key.isBlank()) {
            return SkillOutcome.blocked("No Gemini API key configured (Settings → API connection).")
        }
        val model = appPrefs.aiModel
        val prompt = "Write one short, engaging Instagram caption (max 2 lines) and 12 relevant hashtags " +
                "for an AI-assistant / JARVIS themed short video. Reply as plain text: the caption, then a " +
                "line 'HASHTAGS:' followed by the hashtags separated by spaces."

        return withContext(Dispatchers.IO) {
            try {
                val body = """{"contents":[{"parts":[{"text":${JsonPrimitive(prompt)}}]}]}"""
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/$model:generateContent?key=$key")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        SkillOutcome.failed("Gemini HTTP ${resp.code}: ${text.take(200)}")
                    } else {
                        val parsed = JsonParser.parseString(text).asJsonObject
                        val candidate = parsed.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject
                        val parts = candidate?.getAsJsonObject("content")?.getAsJsonArray("parts")
                        val out = parts?.firstOrNull()?.asJsonObject?.get("text")?.asString
                        if (out.isNullOrBlank()) {
                            SkillOutcome.failed("Gemini returned an empty response.")
                        } else {
                            SkillOutcome.success(
                                "Caption + hashtags generated.",
                                mapOf("caption" to out),
                                verified = "Gemini responded with text"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                SkillOutcome.failed("Caption generation error: ${e.message}")
            }
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

/** Planned: local clip concatenation, logo overlay, music + original-audio preservation, export. */
object VideoEditSkill : Skill {
    override val id = SkillId.VIDEO_EDIT
    override val description = "Combine clips locally, add the authorized logo, keep original audio, export."

    override suspend fun execute(ctx: SkillContext): SkillOutcome =
        SkillOutcome.notImplemented(
            "Not implemented yet. Planned: combine the two clips with MediaMuxer, overlay the user's " +
                    "authorized logo, mix permitted music while preserving original audio where present, and " +
                    "export a local MP4. A logo asset path is required (OpsPreferences.logoPath)."
        )
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
