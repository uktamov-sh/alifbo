package uz.alifbo.keyboard

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout

/** Отладочный экран: поля разных типов, чтобы проверить правила FieldPolicy на устройстве. */
class FieldsTestActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setPadding(40, 40, 40, 40)
        }
        listOf(
            "password" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD),
            "pin" to (InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD),
            "email" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
            "phone" to InputType.TYPE_CLASS_PHONE,
            "text" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES),
        ).forEach { (name, type) ->
            root.addView(EditText(this).apply {
                hint = name
                inputType = type
                textSize = 20f
            })
        }
        setContentView(root)
    }
}
