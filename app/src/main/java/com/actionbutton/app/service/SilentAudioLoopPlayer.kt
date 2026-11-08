package com.actionbutton.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Plays a silent audio loop in the background to ensure volume key broadcast
 * events continue to be dispatched when the display is turned off.
 */
class SilentAudioLoopPlayer(private val context: Context) {

    companion object {
        private const val TAG = "SilentAudioPlayer"
        private const val FILE_NAME = "silent_loop.wav"
    }

    private var mediaPlayer: MediaPlayer? = null
    var isPlaying: Boolean = false
        private set

    fun start() {
        if (isPlaying) return
        try {
            val silentFile = getOrCreateSilentWavFile()
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(silentFile.absolutePath)
                isLooping = true
                setVolume(0.01f, 0.01f)
                prepare()
                start()
            }
            mediaPlayer = player
            isPlaying = true
            Log.d(TAG, "Silent audio loop started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start silent audio player: ${e.message}")
        }
    }

    fun stop() {
        try {
            isPlaying = false
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
            Log.d(TAG, "Silent audio loop stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping silent audio player: ${e.message}")
        }
    }

    private fun getOrCreateSilentWavFile(): File {
        val file = File(context.cacheDir, FILE_NAME)
        if (!file.exists() || file.length() < 1000) {
            createSilentWavFile(file)
        }
        return file
    }

    private fun createSilentWavFile(file: File) {
        val sampleRate = 8000
        val channels = 1
        val bitsPerSample = 16
        val durationSeconds = 1
        val numSamples = sampleRate * durationSeconds
        val dataSize = numSamples * channels * (bitsPerSample / 8)
        val totalSize = 36 + dataSize

        val header = ByteBuffer.allocate(44).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put("RIFF".toByteArray())
            putInt(totalSize)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(sampleRate * channels * (bitsPerSample / 8))
            putShort((channels * (bitsPerSample / 8)).toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray())
            putInt(dataSize)
        }.array()

        val pcmData = ByteArray(dataSize)

        FileOutputStream(file).use { fos ->
            fos.write(header)
            fos.write(pcmData)
            fos.flush()
        }
    }
}
