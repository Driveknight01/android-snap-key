package com.actionbutton.app.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VolumeMute
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class ActionType(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val accentColor: Color,
    val glowColor: Color
) {
    NONE(
        id = "none",
        title = "Không làm gì",
        subtitle = "Tắt cử chỉ này",
        description = "Phím sẽ giữ nguyên chức năng điều chỉnh âm lượng mặc định.",
        accentColor = Color(0xFF8E8E93),
        glowColor = Color(0x338E8E93)
    ),
    FLASHLIGHT(
        id = "flashlight",
        title = "Đèn pin",
        subtitle = "Bật / Tắt tức thì",
        description = "Bật hoặc tắt đèn pin nhanh mà không cần bật sáng màn hình.",
        accentColor = Color(0xFFFF9500),
        glowColor = Color(0x66FF9500)
    ),
    SOUND_MODE(
        id = "sound_mode",
        title = "Chế độ chuông",
        subtitle = "Chuông / Rung / Im lặng",
        description = "Chuyển nhanh giữa chế độ Chuông và Rung.",
        accentColor = Color(0xFFFF375F),
        glowColor = Color(0x66FF375F)
    ),
    CAMERA(
        id = "camera",
        title = "Máy ảnh",
        subtitle = "Chụp ảnh tức thì",
        description = "Mở nhanh máy ảnh trên màn hình khóa.",
        accentColor = Color(0xFF0A84FF),
        glowColor = Color(0x660A84FF)
    ),
    PLAY_PAUSE(
        id = "play_pause",
        title = "Nhạc",
        subtitle = "Phát / Tạm dừng",
        description = "Điều khiển phát hoặc tạm dừng bài hát đang nghe.",
        accentColor = Color(0xFF30D158),
        glowColor = Color(0x6630D158)
    ),
    RECORDER(
        id = "recorder",
        title = "Ghi âm",
        subtitle = "Ghi âm ngầm tức thì",
        description = "Ghi âm trực tiếp dưới nền mà không cần mở khóa.",
        accentColor = Color(0xFFBF5AF2),
        glowColor = Color(0x66BF5AF2)
    ),
    DND(
        id = "dnd",
        title = "Không làm phiền",
        subtitle = "Bật / Tắt DND",
        description = "Bật hoặc tắt chế độ Không làm phiền.",
        accentColor = Color(0xFF5E5CE6),
        glowColor = Color(0x665E5CE6)
    );

    fun getIcon(): ImageVector = when (this) {
        NONE -> Icons.Rounded.Block
        FLASHLIGHT -> Icons.Rounded.FlashlightOn
        SOUND_MODE -> Icons.Rounded.VolumeMute
        CAMERA -> Icons.Rounded.CameraAlt
        PLAY_PAUSE -> Icons.Rounded.PlayArrow
        RECORDER -> Icons.Rounded.Mic
        DND -> Icons.Rounded.Notifications
    }

    companion object {
        fun fromId(id: String): ActionType {
            return entries.find { it.id == id } ?: FLASHLIGHT
        }
    }
}
