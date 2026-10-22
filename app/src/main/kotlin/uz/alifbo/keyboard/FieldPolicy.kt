package uz.alifbo.keyboard

import android.text.InputType

/**
 * Что клавиатура может делать в текущем поле.
 * В секретных полях (пароли, PIN) она не читает текст вокруг курсора и ничего не подсказывает.
 */
data class FieldPolicy(
    val sensitive: Boolean,
    val suggestions: Boolean,
    val converter: Boolean,
    val startOnSymbols: Boolean,
) {
    companion object {
        val DEFAULT = FieldPolicy(sensitive = false, suggestions = false, converter = false, startOnSymbols = false)

        fun of(inputType: Int): FieldPolicy {
            val cls = inputType and InputType.TYPE_MASK_CLASS
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            val isText = cls == InputType.TYPE_CLASS_TEXT

            val sensitive = (isText && variation in setOf(
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            )) || (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)

            val address = isText && variation in setOf(
                InputType.TYPE_TEXT_VARIATION_URI,
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            )
            val converter = isText && !sensitive && !address
            val suggestions = converter && (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) == 0

            return FieldPolicy(
                sensitive = sensitive,
                suggestions = suggestions,
                converter = converter,
                startOnSymbols = cls == InputType.TYPE_CLASS_NUMBER ||
                    cls == InputType.TYPE_CLASS_PHONE ||
                    cls == InputType.TYPE_CLASS_DATETIME,
            )
        }
    }
}
