package com.actionbutton.app.data

import android.content.Context
import android.content.SharedPreferences
import com.actionbutton.app.model.ActionType
import com.actionbutton.app.model.TriggerType

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var hapticFeedbackEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC, value).apply()

    var onlyWhenScreenOff: Boolean
        get() = prefs.getBoolean(KEY_ONLY_SCREEN_OFF, true)
        set(value) = prefs.edit().putBoolean(KEY_ONLY_SCREEN_OFF, value).apply()

    var selectedAction: ActionType
        get() = ActionType.fromId(prefs.getString(KEY_SELECTED_ACTION, ActionType.FLASHLIGHT.id) ?: ActionType.FLASHLIGHT.id)
        set(value) = prefs.edit().putString(KEY_SELECTED_ACTION, value.id).apply()

    var triggerGesture: TriggerType
        get() = TriggerType.fromId(prefs.getString(KEY_TRIGGER_GESTURE, TriggerType.VOL_DOWN_DOUBLE.id) ?: TriggerType.VOL_DOWN_DOUBLE.id)
        set(value) = prefs.edit().putString(KEY_TRIGGER_GESTURE, value.id).apply()

    companion object {
        private const val PREFS_NAME = "action_button_settings"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_HAPTIC = "haptic_feedback"
        private const val KEY_ONLY_SCREEN_OFF = "only_screen_off"
        private const val KEY_SELECTED_ACTION = "selected_action"
        private const val KEY_TRIGGER_GESTURE = "trigger_gesture"
    }
}
