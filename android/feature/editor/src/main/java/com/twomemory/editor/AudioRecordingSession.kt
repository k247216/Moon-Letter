package com.twomemory.editor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

data class RecordedAudio(val file: File, val durationMs: Long, val sizeBytes: Long, val mimeType: String = "audio/mp4")

/** Foreground session owns pending originals; confirmation transfers file ownership. */
internal class AudioRecordingSession(private val context: Context) {
    private val files = RecordingFiles(File(context.filesDir, "recordings"))
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var pending: File? = null
    private var startedAt = 0L
    var preview: RecordedAudio? = null
        private set
    val isRecording: Boolean get() = recorder != null
    val isPlaying: Boolean get() = runCatching { player?.isPlaying == true }.getOrDefault(false)
    val elapsedMs: Long get() = if (isRecording) SystemClock.elapsedRealtime() - startedAt else preview?.durationMs ?: 0L
    val amplitude: Int get() = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    fun start() {
        cancel()
        val file = files.createPending()
        pending = file
        try {
            @Suppress("DEPRECATION")
            val recording = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
            recorder = recording
            recording.setAudioSource(MediaRecorder.AudioSource.MIC)
            recording.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recording.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recording.setAudioSamplingRate(44_100)
            recording.setAudioEncodingBitRate(96_000)
            recording.setOutputFile(file.absolutePath)
            recording.setOnErrorListener { failed, _, _ -> if (recorder === failed) cancel() }
            recording.prepare()
            recording.start()
            startedAt = SystemClock.elapsedRealtime()
        } catch (failure: Exception) {
            cancel()
            throw failure
        }
    }

    fun stop(): RecordedAudio {
        val recording = checkNotNull(recorder)
        recorder = null
        try {
            recording.stop()
        } catch (failure: Exception) {
            cancel()
            throw failure
        } finally {
            recording.release()
        }
        val file = checkNotNull(pending)
        try {
            val metadata = MediaMetadataRetriever()
            val duration = try {
                metadata.setDataSource(file.absolutePath)
                check(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes")
                metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } finally { metadata.release() }
            // Also require the actual playback decoder to accept the original.
            val decoder = MediaPlayer()
            try { decoder.setDataSource(file.absolutePath); decoder.prepare() } finally { decoder.release() }
            check(duration >= 1_000 && file.length() >= 128) { "录音太短，请至少录制一秒后停止" }
            check(file.length() <= 16L*1024*1024) { "本次录音超过16 MB，请分段录制" }
            return RecordedAudio(file, duration, file.length()).also { preview = it }
        } catch (failure: Exception) {
            cancel()
            throw failure
        }
    }

    fun play() {
        releasePlayer()
        val audio = checkNotNull(preview)
        val playback = MediaPlayer()
        player = playback
        playback.setOnCompletionListener { finished -> if (player === finished) releasePlayer() }
        playback.setOnErrorListener { failed, _, _ -> if (player === failed) releasePlayer(); true }
        try {
            playback.setDataSource(audio.file.absolutePath)
            playback.prepare()
            playback.start()
        } catch (failure: Exception) { releasePlayer(); throw failure }
    }

    fun releasePlayer() {
        player?.let { runCatching { it.release() } }
        player = null
    }

    fun confirm(): RecordedAudio? {
        releasePlayer()
        val audio = preview ?: return null
        if (files.accept(audio.file, audio.durationMs) == null) return null
        preview = null
        pending = null
        return audio
    }

    fun cancel() {
        recorder?.let { runCatching { it.reset() }; runCatching { it.release() } }
        recorder = null
        releasePlayer()
        files.discardPending()
        pending = null
        preview = null
    }

    fun interrupt() {
        // A stopped preview survives a brief background trip; never keep the mic.
        if (isRecording) cancel() else releasePlayer()
    }
}
