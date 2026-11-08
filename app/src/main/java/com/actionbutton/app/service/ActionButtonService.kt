package com.actionbutton.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
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
    private var volumeObserver: ContentObserver? = null
    private lateinit var silentAudioPlayer: SilentAudioLoopPlayer

    private var previousVolume = -1
    private var originalVolumeBeforeOff = -1
    private var isRestoringVolume = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VOLUME_CHANGED_ACTION) {
                if (isRestoringVolume) return

                val isScreenOn = powerManager?.isInteractive ?: false
                if (isScreenOn && prefs.onlyWhenScreenOff) return

                val streamType = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, AudioManager.STREAM_MUSIC)
                val currentVolume = intent.getIntExtra(EXTRA_VOLUME_STREAM_VALUE, -1)
                val prevVolume = intent.getIntExtra(EXTRA_PREV_VOLUME_STREAM_VALUE, -1)
                val delta = currentVolume - prevVolume

                if (delta != 0) {
                    val direction = if (delta > 0) 1 else -1
                    val restoreBase = if (prevVolume >= 0) prevVolume else currentVolume
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

        detector = VolumeKeyDetector(Looper.getMainLooper()) { triggerType, restoreToVolume, streamType ->
            onTriggerDetected(triggerType, restoreToVolume, streamType)
        }

        setupWakeLock()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        silentAudioPlayer = SilentAudioLoopPlayer(this)
        setupLocalMediaSession()
        setupVolumeContentObserver()
        registerReceivers()

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

    private fun setupVolumeContentObserver() {
        audioManager?.let { am ->
            previousVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            volumeObserver = object : ContentObserver(mainHandler) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    if (isRestoringVolume) return

                    val isScreenOn = powerManager?.isInteractive ?: false
                    if (isScreenOn && prefs.onlyWhenScreenOff) return

                    val stream = AudioManager.STREAM_MUSIC
                    val currentVolume = am.getStreamVolume(stream)
                    val delta = currentVolume - previousVolume
                    if (delta != 0) {
                        val direction = if (delta > 0) 1 else -1
                        detector.onVolumeChanged(direction, previousVolume, stream)
                        previousVolume = currentVolume
                    }
                }
            }

            try {
                contentResolver.registerContentObserver(
                    Settings.System.CONTENT_URI,
                    true,
                    volumeObserver!!
                )
            } catch (e: Exception) {
                Log.e(TAG, "ContentObserver error: ${e.message}")
            }
        }
    }

    private fun restoreVolumeSilently(streamType: Int, targetVolume: Int) {
        val am = audioManager ?: return
        val validStream = if (streamType in 0..10) streamType else AudioManager.STREAM_MUSIC
        mainHandler.postDelayed({
            try {
                isRestoringVolume = true
                am.setStreamVolume(validStream, targetVolume, 0)
                previousVolume = targetVolume
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore volume: ${e.message}")
            } finally {
                mainHandler.postDelayed({ isRestoringVolume = false }, 150)
            }
        }, 120)
    }

    private fun calibrateVolumeForScreenOff() {
        audioManager?.let { am ->
            val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            originalVolumeBeforeOff = current
            previousVolume = current

            val targetSafeVolume = when {
                current < 3 -> 3
                current > max - 3 -> (max - 3).coerceAtLeast(3)
                else -> current
            }

            if (targetSafeVolume != current) {
                isRestoringVolume = true
                am.setStreamVolume(AudioManager.STREAM_MUSIC, targetSafeVolume, 0)
                previousVolume = targetSafeVolume
                mainHandler.postDelayed({ isRestoringVolume = false }, 150)
            }
        }
    }

    private fun restoreVolumeAfterScreenOn() {
        if (originalVolumeBeforeOff >= 0) {
            audioManager?.let { am ->
                val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (current != originalVolumeBeforeOff) {
                    isRestoringVolume = true
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolumeBeforeOff, 0)
                    previousVolume = originalVolumeBeforeOff
                    mainHandler.postDelayed({ isRestoringVolume = false }, 150)
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

        volumeObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering content observer: ${e.message}")
            }
        }

        mediaSession?.release()
        mediaSession = null
        releaseScreenOffWakeLock()
    }
}
