package uz.alifbo.keyboard

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import uz.alifbo.converter.AlifboConverter

/**
 * Сервис клавиатуры. Текст поля читается только чтобы найти текущее слово для подсказок
 * или по нажатию «eski→yangi», и никуда не сохраняется. В секретных полях текст не читается вообще.
 */
class AlifboIME : InputMethodService(), KeyboardView.Listener {

    private var keyboard: KeyboardView? = null
    private var strip: LinearLayout? = null

    private var language = Page.UZ
    private var page = Page.UZ
    private val shift = ShiftState()
    private var policy = FieldPolicy.DEFAULT
    private var settings = KeyboardSettings.from(emptyMap<String, Any>())
    private var emojiIndex = 0

    private val suggesters = HashMap<Page, Suggester>()
    private val prefs by lazy { getSharedPreferences(KeyboardSettings.FILE, MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        language = if (prefs.getString(PREF_LANGUAGE, "uz") == "ru") Page.RU else Page.UZ
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.keyboard_bg))
            // Увеличенная буква верхнего ряда рисуется поверх полосы подсказок.
            clipChildren = false
            clipToPadding = false
        }
        strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(strip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)))
        keyboard = KeyboardView(this).also {
            it.listener = this
            it.configure(settings, sensitiveField = policy.sensitive)
        }
        root.addView(keyboard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        // С Android 15 окно клавиатуры уходит под системную полосу навигации: поднимаем клавиши над ней.
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bottom = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.navigationBars() or WindowInsets.Type.tappableElement()).bottom
            } else {
                @Suppress("DEPRECATION") insets.systemWindowInsetBottom
            }
            v.setPadding(0, 0, 0, bottom)
            WindowInsets.CONSUMED
        }
        return root
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        policy = FieldPolicy.of(info.inputType)
        settings = KeyboardSettings.read(prefs)
        keyboard?.configure(settings, sensitiveField = policy.sensitive)
        page = if (policy.startOnSymbols) Page.SYMBOLS else language
        refreshShift()
        render()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        // Убираем подсказки, чтобы слова из прошлого поля не оставались на экране и в памяти.
        strip?.removeAllViews()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        refreshShift()
        render()
    }

    private val onLetters get() = page == Page.UZ || page == Page.RU

    private fun refreshShift() {
        if (!onLetters) return
        val info = currentInputEditorInfo ?: return
        val caps = currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0
        shift.setAuto(caps != 0)
    }

    private fun render() {
        keyboard?.setKeys(
            rows = Layouts.forPage(page, emojiIndex),
            upper = onLetters && shift.isUpper,
            locked = shift.mode == ShiftState.Mode.LOCK,
            spaceLabel = getString(if (language == Page.UZ) R.string.space_uz else R.string.space_ru),
            lettersLabel = getString(if (language == Page.UZ) R.string.letters_uz else R.string.letters_ru),
        )
        updateStrip()
    }

    override fun onKey(key: Key, longPress: Boolean) {
        val ic = currentInputConnection ?: return
        if (longPress) {
            when {
                key.action == Action.LANGUAGE -> getSystemService(InputMethodManager::class.java).showInputMethodPicker()
                key.longPress != null -> {
                    ic.commitText(key.longPress, 1)
                    shift.afterLetter()
                }
            }
            render()
            return
        }
        when (key.action) {
            null -> {
                val text = key.text ?: return
                ic.commitText(if (onLetters && shift.isUpper) text.uppercase() else text, 1)
                shift.afterLetter()
            }
            Action.SHIFT -> shift.tap(SystemClock.uptimeMillis())
            Action.BACKSPACE ->
                if (!ic.getSelectedText(0).isNullOrEmpty()) ic.commitText("", 1)
                else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            Action.SPACE -> ic.commitText(" ", 1)
            Action.ENTER -> enter(ic)
            Action.SYMBOLS -> page = Page.SYMBOLS
            Action.SYMBOLS2 -> page = Page.SYMBOLS2
            Action.LETTERS -> page = language
            Action.EMOJI -> page = Page.EMOJI
            Action.EMOJI_PREV -> emojiIndex = (emojiIndex - 1).mod(Layouts.EMOJI_PAGES)
            Action.EMOJI_NEXT -> emojiIndex = (emojiIndex + 1).mod(Layouts.EMOJI_PAGES)
            Action.LANGUAGE -> {
                language = if (language == Page.UZ) Page.RU else Page.UZ
                page = language
                prefs.edit().putString(PREF_LANGUAGE, if (language == Page.RU) "ru" else "uz").apply()
            }
        }
        render()
    }

    private fun enter(ic: InputConnection) {
        val options = currentInputEditorInfo?.imeOptions ?: 0
        val action = options and EditorInfo.IME_MASK_ACTION
        val noAction = (options and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    private fun updateStrip() {
        val s = strip ?: return
        s.removeAllViews()
        if (policy.sensitive) return
        if (policy.converter) s.addView(chip(getString(R.string.convert), accent = true) { convertField() }, chipParams(weighted = false))
        if (!policy.suggestions || !settings.suggestions || !onLetters) return
        val before = currentInputConnection?.getTextBeforeCursor(48, 0) ?: return
        val word = Suggester.currentWord(before)
        if (word.isEmpty()) return
        suggester(language).suggest(word).forEach { w -> s.addView(chip(w) { pick(word, w) }, chipParams(weighted = true)) }
    }

    private fun pick(typed: String, word: String) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        ic.deleteSurroundingText(typed.length, 0)
        ic.commitText("$word ", 1)
        ic.endBatchEdit()
    }

    /**
     * Переписывает текст поля (или выделенный фрагмент) на новый алфавит. Только по нажатию.
     * Текст всегда считается старым: sh/ch переписываются, даже если рядом уже есть Ş или Ç.
     */
    private fun convertField() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText(AlifboConverter.toNewLatin(selected.toString(), assumeOldLatin = true), 1)
            return
        }
        val before = ic.getTextBeforeCursor(MAX_CONVERT, 0)?.toString() ?: ""
        val after = ic.getTextAfterCursor(MAX_CONVERT, 0)?.toString() ?: ""
        val all = before + after
        val converted = AlifboConverter.toNewLatin(all, assumeOldLatin = true)
        if (converted == all) return
        ic.beginBatchEdit()
        ic.deleteSurroundingText(before.length, after.length)
        ic.commitText(converted, 1)
        ic.endBatchEdit()
    }

    private fun suggester(lang: Page) = suggesters.getOrPut(lang) {
        val file = if (lang == Page.RU) "words_ru.txt" else "words_uz.txt"
        Suggester(Suggester.parse(assets.open(file).bufferedReader().use { it.readText() }))
    }

    private fun chip(text: String, accent: Boolean = false, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(12), 0, dp(12), 0)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (accent) 14f else 16f)
        setTextColor(getColor(if (accent) R.color.new_letter else R.color.ink))
        val bg = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, bg, true)
        setBackgroundResource(bg.resourceId)
        setOnClickListener { onClick() }
    }

    private fun chipParams(weighted: Boolean) = if (weighted) {
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
    } else {
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val PREF_LANGUAGE = "language"
        const val MAX_CONVERT = 10_000
    }
}
