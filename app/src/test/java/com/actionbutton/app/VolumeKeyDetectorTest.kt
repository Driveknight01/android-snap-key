package com.actionbutton.app

import com.actionbutton.app.model.ActionType
import com.actionbutton.app.model.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeKeyDetectorTest {

    @Test
    fun testActionTypeLookup() {
        val flashlight = ActionType.fromId("flashlight")
        assertEquals(ActionType.FLASHLIGHT, flashlight)

        val openApp = ActionType.fromId("open_app")
        assertEquals(ActionType.OPEN_APP, openApp)

        val none = ActionType.fromId("none")
        assertEquals(ActionType.NONE, none)

        val invalid = ActionType.fromId("non_existent_action")
        assertEquals(ActionType.NONE, invalid)
    }

    @Test
    fun testTriggerTypeLookup() {
        val downDouble = TriggerType.fromId("vol_down_double")
        assertEquals(TriggerType.VOL_DOWN_DOUBLE, downDouble)

        val upDouble = TriggerType.fromId("vol_up_double")
        assertEquals(TriggerType.VOL_UP_DOUBLE, upDouble)

        val downLong = TriggerType.fromId("vol_down_long")
        assertEquals(TriggerType.VOL_DOWN_LONG, downLong)

        val upLong = TriggerType.fromId("vol_up_long")
        assertEquals(TriggerType.VOL_UP_LONG, upLong)

        val invalid = TriggerType.fromId("invalid_trigger")
        assertEquals(TriggerType.VOL_DOWN_DOUBLE, invalid)
    }
}
