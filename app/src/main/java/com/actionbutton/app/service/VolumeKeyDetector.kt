package com.actionbutton.app.service

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.actionbutton.app.model.TriggerType

class VolumeKeyDetector(
    mainLooper: Looper = Looper.getMainLooper(),
    private val onTriggerAction: (TriggerType, Int, Int) -> Unit
) {
    companion object {
        private const val TAG = "VolumeKeyDetector"
        private const val DOUBLE_CLICK_TIMEOUT_MS = 650L
        private const val DEBOUNCE_MIN_INTERVAL_MS = 40L
        private const val ACTION_COOLDOWN_MS = 380L
    }

    private val handler = Handler(mainLooper)

    private enum class State {
        IDLE,
        FIRST_PRESS_PENDING,
        COOLDOWN
    }

    private var state = State.IDLE
    private var activeDirection = 0
    private var initialVolumeLevel = -1
    private var activeStreamType = -1
    private var lastEventTime = 0L
    private var repeatPressCount = 0

    private val singlePressTimeoutRunnable = Runnable {
        resetState()
    }

    private val cooldownFinishedRunnable = Runnable {
        resetState()
    }

    fun onVolumeChanged(direction: Int, originalVolumeBeforeChange: Int, streamType: Int) {
        val now = SystemClock.uptimeMillis()
        val elapsedSinceLast = now - lastEventTime

        if (elapsedSinceLast < DEBOUNCE_MIN_INTERVAL_MS && direction == activeDirection) {
            return
        }

        if (state == State.COOLDOWN) {
            return
        }

        lastEventTime = now

        when (state) {
            State.IDLE -> {
                state = State.FIRST_PRESS_PENDING
                activeDirection = direction
                initialVolumeLevel = originalVolumeBeforeChange
                activeStreamType = streamType
                repeatPressCount = 1

                handler.postDelayed(singlePressTimeoutRunnable, DOUBLE_CLICK_TIMEOUT_MS)
            }

            State.FIRST_PRESS_PENDING -> {
                if (direction == activeDirection) {
                    repeatPressCount++

                    if (repeatPressCount == 2) {
                        handler.removeCallbacks(singlePressTimeoutRunnable)

                        val trigger = if (activeDirection < 0) TriggerType.VOL_DOWN_DOUBLE else TriggerType.VOL_UP_DOUBLE
                        val restoreTo = initialVolumeLevel
                        val stream = activeStreamType

                        Log.d(TAG, "Double click detected: $trigger")
                        state = State.COOLDOWN
                        handler.postDelayed(cooldownFinishedRunnable, ACTION_COOLDOWN_MS)

                        onTriggerAction(trigger, restoreTo, stream)
                    } else if (repeatPressCount >= 3) {
                        handler.removeCallbacks(singlePressTimeoutRunnable)

                        val trigger = if (activeDirection < 0) TriggerType.VOL_DOWN_LONG else TriggerType.VOL_UP_LONG
                        val restoreTo = initialVolumeLevel
                        val stream = activeStreamType

                        Log.d(TAG, "Long press detected: $trigger")
                        state = State.COOLDOWN
                        handler.postDelayed(cooldownFinishedRunnable, ACTION_COOLDOWN_MS)

                        onTriggerAction(trigger, restoreTo, stream)
                    }
                } else {
                    resetState()
                    onVolumeChanged(direction, originalVolumeBeforeChange, streamType)
                }
            }

            State.COOLDOWN -> {}
        }
    }

    fun resetState() {
        handler.removeCallbacks(singlePressTimeoutRunnable)
        handler.removeCallbacks(cooldownFinishedRunnable)
        state = State.IDLE
        activeDirection = 0
        initialVolumeLevel = -1
        activeStreamType = -1
        repeatPressCount = 0
    }
}
