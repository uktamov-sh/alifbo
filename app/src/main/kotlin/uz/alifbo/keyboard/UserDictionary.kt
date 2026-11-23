package uz.alifbo.keyboard

import android.content.Context

/**
 * Личный словарь: слова, которые пользователь сам добавил кнопкой «+ lüğatga».
 * Клавиатура ничего не запоминает сама. Хранится только на устройстве, в резервные копии не попадает
 * (allowBackup=false, sharedpref исключён в data_extraction_rules), очищается в настройках.
 */
class UserDictionary(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Слова в нижнем регистре. */
    val words: Set<String> get() = prefs.getStringSet(KEY, emptySet()) ?: emptySet()

    val size: Int get() = words.size

    fun add(word: String) {
        prefs.edit().putStringSet(KEY, words + word.lowercase()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val FILE = "user_words"
        const val KEY = "words"
    }
}
