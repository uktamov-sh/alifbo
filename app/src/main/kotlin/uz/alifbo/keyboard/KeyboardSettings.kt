package uz.alifbo.keyboard

import android.content.SharedPreferences

/** Настройки клавиатуры. Хранятся только на устройстве, в резервные копии не попадают. */
data class KeyboardSettings(
    val vibration: Boolean,
    val sound: Boolean,
    val preview: Boolean,
    val suggestions: Boolean,
    val height: Height,
) {
    enum class Height(val keyDp: Int) { SMALL(44), MEDIUM(50), LARGE(58) }

    companion object {
        const val FILE = "settings"
        const val VIBRATION = "vibration"
        const val SOUND = "sound"
        const val PREVIEW = "preview"
        const val SUGGESTIONS = "suggestions"
        const val HEIGHT = "height"

        fun from(values: Map<String, *>) = KeyboardSettings(
            vibration = values[VIBRATION] as? Boolean ?: true,
            sound = values[SOUND] as? Boolean ?: false,
            preview = values[PREVIEW] as? Boolean ?: true,
            suggestions = values[SUGGESTIONS] as? Boolean ?: true,
            height = Height.entries.firstOrNull { it.name == values[HEIGHT] } ?: Height.MEDIUM,
        )

        fun read(prefs: SharedPreferences) = from(prefs.all)
    }
}
