package com.actionbutton.app.actions

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.actionbutton.app.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VoiceRecorderManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceRecorderManager"
        private const val NOTIFICATION_ID = 3001
        private const val CHANNEL_ID = "voice_recorder_channel"
        var isRecording = false
            private set
    }

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    init {
        createNotificationChannel()
    }

    fun toggleRecording() {
        if (isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        try {
            val recordDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS) ?: context.filesDir,
                "ActionRecordings"
            ).apply { if (!exists()) mkdirs() }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(recordDir, "Record_$timestamp.m4a")
            currentOutputFile = file

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            isRecording = true
            Log.i(TAG, "Recording started: ${file.absolutePath}")

            // Double pulse haptic feedback when recording starts
            triggerVibrate(longArrayOf(0, 100, 60, 100))

            showRecordingNotification(file.name)

            mainHandler.post {
                Toast.makeText(context, "Đang ghi âm...", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}")
            isRecording = false
            currentOutputFile = null
            mainHandler.post {
                Toast.makeText(context, "Lỗi khi bắt đầu ghi âm: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
            mediaRecorder = null
            isRecording = false

            val savedFile = currentOutputFile
            Log.i(TAG, "Recording saved: ${savedFile?.absolutePath}")

            // Single longer haptic pulse when recording stops
            triggerVibrate(longArrayOf(0, 220))

            dismissRecordingNotification()

            mainHandler.post {
                Toast.makeText(context, "Đã lưu: ${savedFile?.name}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}")
            isRecording = false
            mediaRecorder = null
        }
    }

    private fun showRecordingNotification(fileName: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Đang ghi âm ngầm...")
            .setContentText("File: $fileName • Bấm nút Action lần nữa để dừng và lưu")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun dismissRecordingNotification() {
        notificationManager?.cancel(NOTIFICATION_ID)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ghi âm ngầm Action Button",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo khi Action Button đang ghi âm ngầm"
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun triggerVibrate(pattern: LongArray) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibrate error: ${e.message}")
        }
    }
}
