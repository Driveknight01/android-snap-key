package com.actionbutton.app.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.ui.graphics.vector.ImageVector

enum class TriggerType(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconDescription: String
) {
    VOL_DOWN_DOUBLE(
        id = "vol_down_double",
        title = "Nhấn đúp Giảm âm lượng",
        subtitle = "Bấm nhanh nút Giảm âm lượng 2 lần",
        iconDescription = "Double click Volume Down"
    ),
    VOL_UP_DOUBLE(
        id = "vol_up_double",
        title = "Nhấn đúp Tăng âm lượng",
        subtitle = "Bấm nhanh nút Tăng âm lượng 2 lần",
        iconDescription = "Double click Volume Up"
    ),
    VOL_DOWN_LONG(
        id = "vol_down_long",
        title = "Nhấn giữ Giảm âm lượng",
        subtitle = "Giữ nút Giảm âm lượng trong 0.5 giây",
        iconDescription = "Long press Volume Down"
    ),
    VOL_UP_LONG(
        id = "vol_up_long",
        title = "Nhấn giữ Tăng âm lượng",
        subtitle = "Giữ nút Tăng âm lượng trong 0.5 giây",
        iconDescription = "Long press Volume Up"
    );

    companion object {
        fun fromId(id: String): TriggerType {
            return entries.find { it.id == id } ?: VOL_DOWN_DOUBLE
        }
    }
}
