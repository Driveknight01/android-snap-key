package com.actionbutton.app.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import com.actionbutton.app.MainActivity
import com.actionbutton.app.R
import com.actionbutton.app.actions.ActionExecutor
import com.actionbutton.app.data.PreferencesManager
import com.actionbutton.app.model.TriggerType

class ActionButtonService : Service() {

    companion object {
        private const val TAG = "ActionButtonService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "action_button_channel"
        const val ACTION_START = "com.actionbutton.app.START"
        const val ACTION_STOP = "com.actionbutton.app.STOP"

        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        private const val EXTRA_VOLUME_STREAM_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE"
        private const val EXTRA_PREV_VOLUME_STREAM_VALUE = "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE"

        var isRunning = false
            private set
    }

    private lateinit var prefs: PreferencesManager
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var detector: VolumeKeyDetector
    private var mediaSession: MediaSessionCompat? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var powerManager: PowerManager? = null
    private var keyguardManager: KeyguardManager? = null
    private lateinit var silentAudioPlayer: SilentAudioLoopPlayer

    private val streamVolumeMap = mutableMapOf<Int, Int>()
    private var originalVolumeBeforeOff = -1
    private var isRestoringVolume = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VOLUME_CHANGED_ACTION) {
                if (isRestoringVolume) return

                val isLocked = keyguardManager?.isKeyguardLocked ?: true
                val isInteractive = powerManager?.isInteractive ?: false

                // Only bypass when the user is actively using an unlocked device with screen on
                if (!isLocked && isInteractive && prefs.onlyWhenScreenOff) {
                    return
                }

                val streamType = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, AudioManager.STREAM_MUSIC)
                val currentVolume = intent.getIntExtra(EXTRA_VOLUME_STREAM_VALUE, -1)
                val prevFromIntent = intent.getIntExtra(EXTRA_PREV_VOLUME_STREAM_VALUE, -1)

                val lastKnown = streamVolumeMap[streamType] ?: prevFromIntent

                val delta = when {
                    lastKnown >= 0 && currentVolume >= 0 -> currentVolume - lastKnown
                    prevFromIntent >= 0 && currentVolume >= 0 -> currentVolume - prevFromIntent
                    else -> 0
                }

                if (currentVolume >= 0) {
                    streamVolumeMap[streamType] = currentVolume
                }

                Log.d(TAG, "Volume event: delta=$delta, cur=$currentVolume, last=$lastKnown, stream=$streamType, isLocked=$isLocked")

                if (delta != 0) {
                    val direction = if (delta > 0) 1 else -1
                    val restoreBase = if (lastKnown >= 0) lastKnown else currentVolume
                    detector.onVolumeChanged(direction, restoreBase, streamType)
                }
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    detector.resetState()
                    acquireScreenOffWakeLock()
                    calibrateVolumeForScreenOff()
                    silentAudioPlayer.start()
                }
                Intent.ACTION_SCREEN_ON -> {
                    detector.resetState()
                    restoreVolumeAfterScreenOn()
                    releaseScreenOffWakeLock()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
        actionExecutor = ActionExecutor(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

        detector = VolumeKeyDetector(Looper.getMainLooper()) { triggerType, restoreToVolume, streamType ->
            onTriggerDetected(triggerType, restoreToVolume, streamType)
        }

        setupWakeLock()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        silentAudioPlayer = SilentAudioLoopPlayer(this)
        setupLocalMediaSession()
        registerReceivers()

        // Cache initial volumes
        audioManager?.let { am ->
            val musicVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            streamVolumeMap[AudioManager.STREAM_MUSIC] = musicVol
            val ringVol = am.getStreamVolume(AudioManager.STREAM_RING)
            streamVolumeMap[AudioManager.STREAM_RING] = ringVol
        }

        silentAudioPlayer.start()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onTriggerDetected(triggerType: TriggerType, restoreToVolume: Int, streamType: Int) {
        if (triggerType == prefs.triggerGesture) {
            acquireBriefWakeLock()
            val action = prefs.selectedAction

            Log.i(TAG, "Triggered action: ${action.id}")
            actionExecutor.execute(action)

            if (restoreToVolume >= 0) {
                restoreVolumeSilently(streamType, restoreToVolume)
            }
        }
    }

    private fun setupLocalMediaSession() {
        try {
            mediaSession = MediaSessionCompat(this, "ActionButtonSession").apply {
                setFlags(
                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
                )

                val playbackState = PlaybackStateCompat.Builder()
                    .setState(
                        PlaybackStateCompat.STATE_PLAYING,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                        1.0f
                    )
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                    .build()
                setPlaybackState(playbackState)
                isActive = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaSession setup error: ${e.message}")
        }
    }

    private fun registerReceivers() {
        val volumeFilter = IntentFilter(VOLUME_CHANGED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(volumeReceiver, volumeFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(volumeReceiver, volumeFilter)
        }

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, screenFilter)
        }
    }

    private fun restoreVolumeSilently(streamType: Int, targetVolume: Int) {
        val am = audioManager ?: return
        val validStream = if (streamType in 0..10) streamType else AudioManager.STREAM_MUSIC
        mainHandler.postDelayed({
            try {
                isRestoringVolume = true
                am.setStreamVolume(validStream, targetVolume, 0)
                streamVolumeMap[validStream] = targetVolume
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore volume: ${e.message}")
            } finally {
                mainHandler.postDelayed({ isRestoringVolume = false }, 100)
            }
        }, 80)
    }

    private fun calibrateVolumeForScreenOff() {
        audioManager?.let { am ->
            val stream = AudioManager.STREAM_MUSIC
            val current = am.getStreamVolume(stream)
            val max = am.getStreamMaxVolume(stream)
            originalVolumeBeforeOff = current

            val targetSafeVolume = when {
                current < 4 -> 4
                current > max - 4 -> (max - 4).coerceAtLeast(4)
                else -> current
            }

            streamVolumeMap[stream] = targetSafeVolume

            if (targetSafeVolume != current) {
                isRestoringVolume = true
                am.setStreamVolume(stream, targetSafeVolume, 0)
                mainHandler.postDelayed({ isRestoringVolume = false }, 100)
            }
        }
    }

    private fun restoreVolumeAfterScreenOn() {
        if (originalVolumeBeforeOff >= 0) {
            audioManager?.let { am ->
                val stream = AudioManager.STREAM_MUSIC
                val current = am.getStreamVolume(stream)
                if (current != originalVolumeBeforeOff) {
                    isRestoringVolume = true
                    am.setStreamVolume(stream, originalVolumeBeforeOff, 0)
                    streamVolumeMap[stream] = originalVolumeBeforeOff
                    mainHandler.postDelayed({ isRestoringVolume = false }, 100)
                }
            }
            originalVolumeBeforeOff = -1
        }
    }

    private fun setupWakeLock() {
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ActionButton::ServiceWakeLock"
        )
    }

    private fun acquireScreenOffWakeLock() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(15 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock error: ${e.message}")
        }
    }

    private fun releaseScreenOffWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock release error: ${e.message}")
        }
    }

    private fun acquireBriefWakeLock() {
        try {
            wakeLock?.acquire(3000)
        } catch (e: Exception) {
            Log.e(TAG, "Brief WakeLock error: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_desc)
                setShowBadge(false)
            }
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_desc))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        detector.resetState()
        silentAudioPlayer.stop()

        try {
            unregisterReceiver(volumeReceiver)
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receivers: ${e.message}")
        }

        mediaSession?.release()
        mediaSession = null
        releaseScreenOffWakeLock()
    }
}
