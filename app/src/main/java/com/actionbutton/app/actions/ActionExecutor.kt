package com.actionbutton.app.actions

import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.actionbutton.app.R
import com.actionbutton.app.data.PreferencesManager
import com.actionbutton.app.model.ActionType

class ActionExecutor(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val prefs = PreferencesManager(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val voiceRecorderManager = VoiceRecorderManager(context)

    companion object {
        private const val TAG = "ActionExecutor"
        private const val CHANNEL_CAMERA_LAUNCH_ID = "action_button_camera_channel"
        private var isFlashlightOn = false
    }

    init {
        registerTorchCallback()
    }

    private fun registerTorchCallback() {
        try {
            cameraManager?.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    super.onTorchModeChanged(cameraId, enabled)
                    isFlashlightOn = enabled
                }
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register torch callback: ${e.message}")
        }
    }

    fun execute(actionType: ActionType) {
        Log.d(TAG, "Executing action: ${actionType.id}")

        if (actionType == ActionType.NONE) {
            return
        }

        // Recorder manages its own distinct vibration feedback
        if (prefs.hapticFeedbackEnabled && actionType != ActionType.RECORDER && actionType != ActionType.SOUND_MODE) {
            triggerHapticFeedback()
        }

        when (actionType) {
            ActionType.NONE -> {}
            ActionType.FLASHLIGHT -> toggleFlashlight()
            ActionType.SOUND_MODE -> toggleSoundMode()
            ActionType.CAMERA -> openCamera()
            ActionType.PLAY_PAUSE -> togglePlayPause()
            ActionType.RECORDER -> voiceRecorderManager.toggleRecording()
            ActionType.DND -> toggleDoNotDisturb()
        }
    }

    private fun toggleFlashlight() {
        try {
            val cm = cameraManager ?: return
            val cameraId = getBackCameraId(cm) ?: return
            val nextState = !isFlashlightOn
            cm.setTorchMode(cameraId, nextState)
            isFlashlightOn = nextState
            Log.i(TAG, "Flashlight toggled to: $nextState")
        } catch (e: CameraAccessException) {
            Log.e(TAG, "CameraAccessException toggling flashlight: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling flashlight: ${e.message}")
        }
    }

    private fun getBackCameraId(cm: CameraManager): String? {
        try {
            for (id in cm.cameraIdList) {
                val chars = cm.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                if (facing == CameraCharacteristics.LENS_FACING_BACK && hasFlash) {
                    return id
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving camera id: ${e.message}")
        }
        return null
    }

    /**
     * Toggles between Normal (Ring) and Vibrate.
     * Switching between NORMAL and VIBRATE does not require DND permissions on Android,
     * avoiding SecurityException when DND access is not granted.
     */
    private fun toggleSoundMode() {
        val am = audioManager ?: return
        try {
            val currentMode = am.ringerMode
            val nextMode = if (currentMode == AudioManager.RINGER_MODE_NORMAL) {
                AudioManager.RINGER_MODE_VIBRATE
            } else {
                AudioManager.RINGER_MODE_NORMAL
            }
            am.ringerMode = nextMode

            val modeName = if (nextMode == AudioManager.RINGER_MODE_NORMAL) "Chuông" else "Rung"
            Log.i(TAG, "Sound mode switched to: $modeName (prev: $currentMode)")

            if (prefs.hapticFeedbackEnabled) {
                if (nextMode == AudioManager.RINGER_MODE_VIBRATE) {
                    triggerDoubleHapticPulse()
                } else {
                    triggerSingleHapticPulse()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling ringer mode: ${e.message}")
        }
    }

    /**
     * Launches the camera over lockscreen when screen is off/locked.
     * Uses WakeLock + High-Priority Notification with FullScreenIntent to bypass
     * background activity launch restrictions.
     */
    private fun openCamera() {
        try {
            wakeUpScreen()

            val cameraIntent = getCameraIntent().apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                1002,
                cameraIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_CAMERA_LAUNCH_ID,
                    "Camera Launch",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
                notificationManager?.createNotificationChannel(channel)
            }

            val fullScreenNotif = NotificationCompat.Builder(context, CHANNEL_CAMERA_LAUNCH_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Máy ảnh")
                .setContentText("Đang mở máy ảnh...")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
                .setTimeoutAfter(2000)
                .build()

            notificationManager?.notify(1003, fullScreenNotif)
            mainHandler.postDelayed({ notificationManager?.cancel(1003) }, 2000)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = ActivityOptions.makeBasic().apply {
                    setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                }.toBundle()
                try {
                    pendingIntent.send(context, 0, null, null, null, null, options)
                } catch (_: Exception) {}
            } else {
                try {
                    pendingIntent.send()
                } catch (_: Exception) {}
            }

            try {
                context.startActivity(cameraIntent)
            } catch (_: Exception) {}

            Log.i(TAG, "Camera launch intent dispatched")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching camera: ${e.message}")
        }
    }

    private fun getCameraIntent(): Intent {
        val pm = context.packageManager

        // 1. Try secure lockscreen camera intent
        val secureIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE)
        if (secureIntent.resolveActivity(pm) != null) {
            return secureIntent
        }

        // 2. Try standard still image camera intent
        val standardIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        if (standardIntent.resolveActivity(pm) != null) {
            return standardIntent
        }

        // 3. Try resolving known OEM camera packages (Realme / Oppo / OnePlus / Android)
        val oemPackages = listOf(
            "com.oplus.camera",
            "com.coloros.camera",
            "com.android.camera",
            "com.google.android.GoogleCamera"
        )
        for (pkg in oemPackages) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                return launchIntent
            }
        }

        // 4. Fallback capture intent
        return Intent(MediaStore.ACTION_IMAGE_CAPTURE)
    }

    private fun wakeUpScreen() {
        try {
            @Suppress("DEPRECATION")
            val wakeLock = powerManager?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "ActionButton:CameraWake"
            )
            wakeLock?.acquire(3000)
        } catch (e: Exception) {
            Log.e(TAG, "Wake up screen error: ${e.message}")
        }
    }

    private fun togglePlayPause() {
        val am = audioManager ?: return
        try {
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            am.dispatchMediaKeyEvent(downEvent)
            am.dispatchMediaKeyEvent(upEvent)
        } catch (e: Exception) {
            Log.e(TAG, "Error dispatching media play/pause: ${e.message}")
        }
    }

    private fun toggleDoNotDisturb() {
        val nm = notificationManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!nm.isNotificationPolicyAccessGranted) {
                    mainHandler.post {
                        Toast.makeText(context, "Cần cấp quyền Không làm phiền trong Cài đặt", Toast.LENGTH_SHORT).show()
                    }
                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    return
                }

                val currentFilter = nm.currentInterruptionFilter
                val nextFilter = if (currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY
                } else {
                    NotificationManager.INTERRUPTION_FILTER_ALL
                }
                nm.setInterruptionFilter(nextFilter)
                Log.i(TAG, "DND filter changed to: $nextFilter")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling DND filter: ${e.message}")
        }
    }

    private fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(45)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Haptic feedback error: ${e.message}")
        }
    }

    private fun triggerSingleHapticPulse() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(VibrationEffect.createOneShot(75, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {}
    }

    private fun triggerDoubleHapticPulse() {
        try {
            val timings = longArrayOf(0, 50, 70, 50)
            val amplitudes = intArrayOf(0, 180, 0, 220)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            }
        } catch (_: Exception) {}
    }
}
