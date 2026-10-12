package com.jarvis.assistant.ops.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

data class VideoExportResult(
    val ok: Boolean,
    val message: String,
    val outputPath: String? = null
)

/**
 * Local video assembly using AndroidX Media3 Transformer (on-device, no network, no cloud).
 *
 * - Combines clips in order into a single MP4.
 * - Optionally burns the user's authorized logo as a static overlay.
 * - Preserves the original audio of each clip.
 *
 * Music mixing is not applied yet; the skill reports that honestly.
 */
object LocalVideoEditor {

    /** Duration (ms) of a media file, or -1 if unreadable. Used to verify an export. */
    fun probeDurationMs(file: File): Long = runCatching {
        val r = MediaMetadataRetriever()
        r.setDataSource(file.absolutePath)
        val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: -1L
        r.release()
        d
    }.getOrDefault(-1L)

    fun probeWidthHeight(file: File): Pair<Int, Int> = runCatching {
        val r = MediaMetadataRetriever()
        r.setDataSource(file.absolutePath)
        val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        r.release()
        w to h
    }.getOrDefault(0 to 0)

    private fun loadLogo(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val f = File(path)
        if (!f.exists()) return null
        return runCatching {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeFile(f.absolutePath, opts)
        }.getOrNull()
    }

    /**
     * Combine [clips] into [output], burning [logoPath] if provided. Runs on the main thread
     * (Transformer requires a Looper) and suspends until the export finishes or errors.
     */
    suspend fun export(
        context: Context,
        clips: List<File>,
        output: File,
        logoPath: String?
    ): VideoExportResult = withContext(Dispatchers.Main) {
        if (clips.isEmpty()) {
            return@withContext VideoExportResult(false, "No input clips provided.")
        }
        if (output.exists()) output.delete()
        output.parentFile?.mkdirs()

        val logoBitmap = loadLogo(logoPath)
        val logoEffect: Effect? = logoBitmap?.let {
            OverlayEffect(ImmutableList.of<TextureOverlay>(BitmapOverlay.createStaticBitmapOverlay(it)))
        }

        val items = clips.map { clip ->
            val builder = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(clip)))
            if (logoEffect != null) {
                builder.setEffects(
                    Effects(ImmutableList.of<AudioProcessor>(), ImmutableList.of<Effect>(logoEffect))
                )
            }
            builder.build()
        }

        val sequence = EditedMediaItemSequence(items)
        val composition = Composition.Builder(listOf(sequence)).build()

        suspendCancellableCoroutine { cont ->
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (cont.isActive) {
                        cont.resume(VideoExportResult(true, "Export completed.", output.absolutePath))
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exception: ExportException
                ) {
                    if (cont.isActive) {
                        cont.resume(VideoExportResult(false, "Export failed: ${exception.message}"))
                    }
                }
            }

            val transformer = Transformer.Builder(context.applicationContext)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(listener)
                .build()

            try {
                transformer.start(composition, output.absolutePath)
            } catch (e: Exception) {
                if (cont.isActive) {
                    cont.resume(VideoExportResult(false, "Could not start export: ${e.message}"))
                }
            }
        }
    }
}
