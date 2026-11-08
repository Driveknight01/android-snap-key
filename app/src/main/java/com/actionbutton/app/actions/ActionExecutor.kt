package com.actionbutton.app.actions

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import com.actionbutton.app.data.PreferencesManager
import com.actionbutton.app.model.ActionType

class ActionExecutor(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    private val prefs = PreferencesManager(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val voiceRecorderManager = VoiceRecorderManager(context)

    companion object {
        private const val TAG = "ActionExecutor"
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

        if (prefs.hapticFeedbackEnabled && actionType != ActionType.RECORDER) {
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

    private fun toggleSoundMode() {
        val am = audioManager ?: return
        try {
            val currentMode = am.ringerMode
            val nextMode = when (currentMode) {
                AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                else -> AudioManager.RINGER_MODE_NORMAL
            }
            am.ringerMode = nextMode

            val modeName = when (nextMode) {
                AudioManager.RINGER_MODE_NORMAL -> "Chuông"
                AudioManager.RINGER_MODE_VIBRATE -> "Rung"
                AudioManager.RINGER_MODE_SILENT -> "Im lặng"
                else -> "Bình thường"
            }
            Log.i(TAG, "Sound mode changed to: $modeName")

            if (nextMode == AudioManager.RINGER_MODE_VIBRATE) {
                triggerDoubleHapticPulse()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling ringer mode: ${e.message}")
        }
    }

    private fun openCamera() {
        try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to launch camera: ${ex.message}")
            }
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
