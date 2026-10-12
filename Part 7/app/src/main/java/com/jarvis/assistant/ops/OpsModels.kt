package com.jarvis.assistant.ops

/**
 * Core models for the JARVIS autonomous operations subsystem
 * (persistent task scheduler + modular skills + local logs/reports).
 *
 * Design rules enforced across this subsystem:
 *  - Never store passwords or tokens.
 *  - Never bypass Android/platform security protections.
 *  - Never claim a capability that is not actually implemented (see [SkillStatus.NOT_IMPLEMENTED]).
 *  - Verify important actions; require approval when a decision is uncertain.
 */

/** Every skill the autonomous pipeline knows about. */
enum class SkillId(val key: String, val title: String) {
    DAILY_REPORT("daily_report", "Daily Report"),
    CAPTION_HASHTAGS("caption_hashtags", "AI Captions & Hashtags"),
    FLOW_VIDEO("flow_video", "Google Flow Video Generation"),
    VIDEO_EDIT("video_edit", "Local Video Edit & Export"),
    INSTAGRAM_PUBLISH("instagram_publish", "Instagram Publishing"),
    POST_FOLLOW_UP("post_follow_up", "Post-Publish Follow-Up");

    companion object {
        fun fromKey(k: String?): SkillId? = values().firstOrNull { it.key == k }
    }
}

/** Outcome classification for a single skill execution. */
enum class SkillStatus {
    SUCCESS,          // completed and verified
    SKIPPED,          // intentionally not needed this run
    NEEDS_APPROVAL,   // paused pending a human decision (e.g. uncertain account)
    NOT_IMPLEMENTED,  // honestly declared: this capability is not built yet
    FAILED,           // attempted and failed after retries
    BLOCKED           // cannot proceed (missing permission / config)
}

/** Lifecycle status of a scheduled/manual run. */
enum class RunStatus { PENDING, RUNNING, SUCCESS, FAILED, BLOCKED, CANCELLED, NEEDS_APPROVAL }

/** Log severity for the local run log. */
enum class LogLevel { INFO, WARN, ERROR, ACTION, VERIFY }

/** Result returned by a skill. */
data class SkillOutcome(
    val status: SkillStatus,
    val message: String,
    val data: Map<String, String> = emptyMap(),
    /** Human-readable evidence that an important action was actually verified. */
    val verificationNote: String? = null
) {
    companion object {
        fun success(msg: String, data: Map<String, String> = emptyMap(), verified: String? = null) =
            SkillOutcome(SkillStatus.SUCCESS, msg, data, verified)

        fun skipped(msg: String) = SkillOutcome(SkillStatus.SKIPPED, msg)
        fun needsApproval(msg: String) = SkillOutcome(SkillStatus.NEEDS_APPROVAL, msg)
        fun notImplemented(msg: String) = SkillOutcome(SkillStatus.NOT_IMPLEMENTED, msg)
        fun failed(msg: String) = SkillOutcome(SkillStatus.FAILED, msg)
        fun blocked(msg: String) = SkillOutcome(SkillStatus.BLOCKED, msg)
    }
}

/** A daily posting slot, expressed in IST. */
data class Slot(val hour: Int, val minute: Int, val label: String) {
    val display: String get() = "%02d:%02d".format(hour, minute)
}
