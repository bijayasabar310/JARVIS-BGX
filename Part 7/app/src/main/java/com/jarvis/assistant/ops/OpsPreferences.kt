package com.jarvis.assistant.ops

import android.content.Context
import android.content.SharedPreferences

/**
 * Preferences for the autonomous operations subsystem.
 *
 * Only non-secret configuration lives here. No passwords or tokens are ever stored;
 * account access relies entirely on the user's already-signed-in apps/sessions.
 */
class OpsPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** Master switch for the autonomous scheduler. */
    var enabled: Boolean
        get() = prefs.getBoolean(K_ENABLED, false)
        set(v) = prefs.edit().putBoolean(K_ENABLED, v).apply()

    /** Emergency stop — when true the runner refuses to start and aborts mid-run. */
    var emergencyStop: Boolean
        get() = prefs.getBoolean(K_EMERGENCY, false)
        set(v) = prefs.edit().putBoolean(K_EMERGENCY, v).apply()

    /** Comma-separated daily slots in IST, e.g. "08:30,13:00,19:00". */
    var slotsRaw: String
        get() = prefs.getString(K_SLOTS, DEFAULT_SLOTS) ?: DEFAULT_SLOTS
        set(v) = prefs.edit().putString(K_SLOTS, v).apply()

    /** Delay (minutes) before the post-publish follow-up. */
    var followUpDelayMinutes: Int
        get() = prefs.getInt(K_FOLLOWUP, 60)
        set(v) = prefs.edit().putInt(K_FOLLOWUP, v.coerceIn(5, 24 * 60)).apply()

    /** Maximum bounded retry attempts per skill. */
    var maxRetries: Int
        get() = prefs.getInt(K_RETRIES, 3)
        set(v) = prefs.edit().putInt(K_RETRIES, v.coerceIn(1, 5)).apply()

    /** Optional path to the user's authorized logo used when compositing video. */
    var logoPath: String
        get() = prefs.getString(K_LOGO, "") ?: ""
        set(v) = prefs.edit().putString(K_LOGO, v).apply()

    /** Folder to read input video clips from. Blank = <filesDir>/clips. */
    var clipsDir: String
        get() = prefs.getString(K_CLIPS_DIR, "") ?: ""
        set(v) = prefs.edit().putString(K_CLIPS_DIR, v.trim()).apply()

    /** Optional permitted music track (reserved; mixing is not applied yet). */
    var musicPath: String
        get() = prefs.getString(K_MUSIC, "") ?: ""
        set(v) = prefs.edit().putString(K_MUSIC, v.trim()).apply()

    /** Approval required before publishing to an account that is not confidently identified. */
    var requireAccountApproval: Boolean
        get() = prefs.getBoolean(K_APPROVAL, true)
        set(v) = prefs.edit().putBoolean(K_APPROVAL, v).apply()

    /**
     * Gemini model id used for the caption skill. Blank = auto-select a generateContent-capable
     * model (the app's voice model is Live-API-only and cannot be used here).
     */
    var captionModel: String
        get() = prefs.getString(K_CAPTION_MODEL, "") ?: ""
        set(v) = prefs.edit().putString(K_CAPTION_MODEL, v.trim()).apply()

    fun slots(): List<Slot> = parseSlots(slotsRaw)

    companion object {
        private const val PREF = "jarvis_ops"
        private const val K_ENABLED = "ops_enabled"
        private const val K_EMERGENCY = "ops_emergency_stop"
        private const val K_SLOTS = "ops_slots"
        private const val K_FOLLOWUP = "ops_followup_minutes"
        private const val K_RETRIES = "ops_max_retries"
        private const val K_LOGO = "ops_logo_path"
        private const val K_CLIPS_DIR = "ops_clips_dir"
        private const val K_MUSIC = "ops_music_path"
        private const val K_APPROVAL = "ops_require_account_approval"
        private const val K_CAPTION_MODEL = "ops_caption_model"

        /** Default daily posting slots in IST. */
        const val DEFAULT_SLOTS = "08:30,13:00,19:00"

        fun parseSlots(raw: String): List<Slot> =
            raw.split(',', ';', '\n')
                .map { it.trim() }
                .filter { it.matches(Regex("\\d{1,2}:\\d{2}")) }
                .map {
                    val (h, m) = it.split(":")
                    Slot(h.toInt().coerceIn(0, 23), m.toInt().coerceIn(0, 59), "$it IST")
                }
                .distinctBy { it.hour * 60 + it.minute }
                .sortedBy { it.hour * 60 + it.minute }
    }
}
