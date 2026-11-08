package com.actionbutton.app.service

import android.os.SystemClock
import android.util.Log
import com.actionbutton.app.model.TriggerType

class VolumeKeyDetector(
    private val onTriggerAction: (TriggerType) -> Unit
) {
    companion object {
        private const val TAG = "VolumeKeyDetector"
        private const val DOUBLE_CLICK_MIN_MS = 65L
        private const val DOUBLE_CLICK_MAX_MS = 700L
        private const val LONG_PRESS_WINDOW_MS = 500L
    }

    private var lastVolDownTime = 0L
    private var lastVolUpTime = 0L
    private var volDownRepeatCount = 0
    private var volUpRepeatCount = 0

    fun onVolumeKeyPressed(direction: Int) {
        val now = SystemClock.elapsedRealtime()

        if (direction < 0) {
            // Volume Down
            val diff = now - lastVolDownTime
            Log.d(TAG, "Volume Down input: diff=${diff}ms, repeatCount=$volDownRepeatCount")

            if (diff in DOUBLE_CLICK_MIN_MS..DOUBLE_CLICK_MAX_MS) {
                volDownRepeatCount++
                if (volDownRepeatCount >= 2) {
                    // Check if consecutive rapid repeats constitute a Long Press
                    if (volDownRepeatCount >= 3 && diff < 250L) {
                        Log.i(TAG, "VOL_DOWN_LONG triggered")
                        lastVolDownTime = 0L
                        volDownRepeatCount = 0
                        onTriggerAction(TriggerType.VOL_DOWN_LONG)
                        return
                    }

                    Log.i(TAG, "VOL_DOWN_DOUBLE triggered (diff: ${diff}ms)")
                    lastVolDownTime = 0L
                    volDownRepeatCount = 0
                    onTriggerAction(TriggerType.VOL_DOWN_DOUBLE)
                }
            } else if (diff > DOUBLE_CLICK_MAX_MS || lastVolDownTime == 0L) {
                // First press or timeout exceeded
                lastVolDownTime = now
                volDownRepeatCount = 1
            }
            // diff < DOUBLE_CLICK_MIN_MS: ignored as duplicate listener bounce
        } else if (direction > 0) {
            // Volume Up
            val diff = now - lastVolUpTime
            Log.d(TAG, "Volume Up input: diff=${diff}ms, repeatCount=$volUpRepeatCount")

            if (diff in DOUBLE_CLICK_MIN_MS..DOUBLE_CLICK_MAX_MS) {
                volUpRepeatCount++
                if (volUpRepeatCount >= 2) {
                    if (volUpRepeatCount >= 3 && diff < 250L) {
                        Log.i(TAG, "VOL_UP_LONG triggered")
                        lastVolUpTime = 0L
                        volUpRepeatCount = 0
                        onTriggerAction(TriggerType.VOL_UP_LONG)
                        return
                    }

                    Log.i(TAG, "VOL_UP_DOUBLE triggered (diff: ${diff}ms)")
                    lastVolUpTime = 0L
                    volUpRepeatCount = 0
                    onTriggerAction(TriggerType.VOL_UP_DOUBLE)
                }
            } else if (diff > DOUBLE_CLICK_MAX_MS || lastVolUpTime == 0L) {
                lastVolUpTime = now
                volUpRepeatCount = 1
            }
        }
    }

    fun reset() {
        lastVolDownTime = 0L
        lastVolUpTime = 0L
        volDownRepeatCount = 0
        volUpRepeatCount = 0
    }
}
