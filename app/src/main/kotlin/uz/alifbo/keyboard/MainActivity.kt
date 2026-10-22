package uz.alifbo.keyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView

/** Экран установки: включить клавиатуру, выбрать её и попробовать. */
class MainActivity : Activity() {

    private val imm by lazy { getSystemService(InputMethodManager::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.enable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.choose).setOnClickListener { imm.showInputMethodPicker() }
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        findViewById<TextView>(R.id.footer).text = getString(R.string.footer, version)
        bindSettings()
    }

    /** Переключатели сохраняются сразу; клавиатура читает их при следующем открытии. */
    private fun bindSettings() {
        val prefs = getSharedPreferences(KeyboardSettings.FILE, MODE_PRIVATE)
        val current = KeyboardSettings.read(prefs)
        mapOf(
            R.id.setting_vibration to (KeyboardSettings.VIBRATION to current.vibration),
            R.id.setting_sound to (KeyboardSettings.SOUND to current.sound),
            R.id.setting_preview to (KeyboardSettings.PREVIEW to current.preview),
            R.id.setting_suggestions to (KeyboardSettings.SUGGESTIONS to current.suggestions),
        ).forEach { (id, setting) ->
            val (key, value) = setting
            findViewById<Switch>(id).apply {
                isChecked = value
                setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(key, checked).apply() }
            }
        }
        val heights = mapOf(
            R.id.height_small to KeyboardSettings.Height.SMALL,
            R.id.height_medium to KeyboardSettings.Height.MEDIUM,
            R.id.height_large to KeyboardSettings.Height.LARGE,
        )
        findViewById<RadioGroup>(R.id.setting_height).apply {
            check(heights.entries.first { it.value == current.height }.key)
            setOnCheckedChangeListener { _, id ->
                heights[id]?.let { prefs.edit().putString(KeyboardSettings.HEIGHT, it.name).apply() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    // Окно выбора клавиатуры не ставит экран на паузу, поэтому обновляемся и при возврате фокуса.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updateStatus()
    }

    private fun updateStatus() {
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val selected = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith("$packageName/") == true
        findViewById<TextView>(R.id.status).setText(
            when {
                selected -> R.string.status_ready
                enabled -> R.string.status_enabled
                else -> R.string.status_off
            }
        )
        findViewById<Button>(R.id.choose).isEnabled = enabled
    }
}
