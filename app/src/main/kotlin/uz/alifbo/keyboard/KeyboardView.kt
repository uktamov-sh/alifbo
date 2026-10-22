package uz.alifbo.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import kotlin.math.hypot

/** Рисует клавиши и сообщает о нажатиях. Ничего не знает о тексте и полях ввода. */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onKey(key: Key, longPress: Boolean)
    }

    var listener: Listener? = null

    private var rows: List<List<Key>> = emptyList()
    private var upper = false
    private var locked = false
    private var spaceLabel = ""
    private var lettersLabel = ""

    private val dp = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity
    private var keyHeight = KeyboardSettings.Height.MEDIUM.keyDp * dp
    private val rowGap = 8 * dp
    private val keyGap = 5 * dp
    private val pad = 4 * dp
    private val radius = 7 * dp

    private var haptics = true
    private var sound = false
    private var preview = true
    private var sensitiveField = false

    private val colorKey = context.getColor(R.color.key)
    private val colorSpecial = context.getColor(R.color.key_special)
    private val colorPressed = context.getColor(R.color.key_pressed)
    private val colorShadow = context.getColor(R.color.key_shadow)
    private val colorAccent = context.getColor(R.color.accent)
    private val colorOnAccent = context.getColor(R.color.on_accent)
    private val colorInk = context.getColor(R.color.ink)
    private val colorMuted = context.getColor(R.color.muted)
    private val colorNewLetter = context.getColor(R.color.new_letter)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        textSize = 10 * sp
        color = colorMuted
    }

    private val keyRects = ArrayList<Pair<Key, RectF>>()
    private val pressed = HashMap<Int, Key>()
    private val longFired = HashSet<Int>()
    private val timers = HashMap<Int, Runnable>()
    private val handler = Handler(Looper.getMainLooper())
    private var previewKey: Key? = null

    private val audio = context.getSystemService(AudioManager::class.java)
    private val a11y = context.getSystemService(AccessibilityManager::class.java)
    private var hovered = -1
    private var a11yFocused = -1

    fun configure(settings: KeyboardSettings, sensitiveField: Boolean) {
        val height = settings.height.keyDp * dp
        val resized = height != keyHeight
        keyHeight = height
        haptics = settings.vibration
        sound = settings.sound
        // В полях паролей увеличенную букву не показываем: её видно со стороны.
        preview = settings.preview && !sensitiveField
        this.sensitiveField = sensitiveField
        if (resized) requestLayout()
    }

    /** Звук идёт в наушники, а не в динамик: тогда TalkBack может называть буквы пароля. */
    private fun privateAudio(): Boolean =
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type in setOf(
                AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_USB_HEADSET,
            )
        }

    fun setKeys(rows: List<List<Key>>, upper: Boolean, locked: Boolean, spaceLabel: String, lettersLabel: String) {
        val resized = rows.size != this.rows.size
        this.rows = rows
        this.upper = upper
        this.locked = locked
        this.spaceLabel = spaceLabel
        this.lettersLabel = lettersLabel
        if (resized) requestLayout()
        layoutKeys()
        invalidate()
        if (a11y.isEnabled) sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val n = rows.size.coerceAtLeast(4)
        setMeasuredDimension(width, (pad * 2 + n * keyHeight + (n - 1) * rowGap).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = layoutKeys()

    private fun layoutKeys() {
        keyRects.clear()
        if (width == 0) return
        var y = pad
        for (row in rows) {
            val total = row.sumOf { it.width.toDouble() }.toFloat()
            val available = width - 2 * pad - keyGap * (row.size - 1)
            var x = pad
            for (key in row) {
                val w = available * key.width / total
                keyRects += key to RectF(x, y, x + w, y + keyHeight)
                x += w + keyGap
            }
            y += keyHeight + rowGap
        }
    }

    override fun onDraw(canvas: Canvas) {
        for ((i, pair) in keyRects.withIndex()) {
            val (key, r) = pair
            fill.color = colorShadow
            canvas.drawRoundRect(r.left, r.top + dp, r.right, r.bottom + dp, radius, radius, fill)
            fill.color = when {
                pressed.containsValue(key) || i == a11yFocused -> colorPressed
                key.action == Action.ENTER -> colorAccent
                key.action == Action.SHIFT && upper -> colorAccent
                key.action != null && key.action != Action.SPACE -> colorSpecial
                else -> colorKey
            }
            canvas.drawRoundRect(r, radius, radius, fill)

            val size = when (key.action) {
                null, Action.SHIFT, Action.BACKSPACE, Action.ENTER, Action.EMOJI -> 22
                Action.SPACE -> 13
                else -> 16
            }
            label.textSize = size * sp
            label.typeface = if (key.isNewLetter) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            label.color = when {
                key.action == Action.ENTER || (key.action == Action.SHIFT && upper) -> colorOnAccent
                key.isNewLetter -> colorNewLetter
                key.action == Action.SPACE -> colorMuted
                else -> colorInk
            }
            drawCentered(canvas, labelOf(key), r.centerX(), r.centerY())

            if (key.text != null && key.longPress != null) {
                canvas.drawText(Layouts.withCase(key.longPress, upper), r.right - 4 * dp, r.top + 12 * dp, hint)
            }
        }
        drawPreview(canvas)
    }

    /**
     * Увеличенная буква над нажатой клавишей. Рисуется выше своих границ, поверх полосы подсказок:
     * у родителя отключена обрезка (clipChildren = false).
     */
    private fun drawPreview(canvas: Canvas) {
        val key = previewKey ?: return
        val r = keyRects.firstOrNull { it.first == key }?.second ?: return
        val w = maxOf(r.width() * 1.3f, 44 * dp)
        val bubble = RectF(r.centerX() - w / 2, r.top + 8 * dp - 52 * dp, r.centerX() + w / 2, r.top + 8 * dp)
        bubble.offset(-minOf(0f, bubble.left - pad) - maxOf(0f, bubble.right - (width - pad)), 0f)
        fill.color = colorShadow
        canvas.drawRoundRect(bubble.left, bubble.top + dp, bubble.right, bubble.bottom + dp, radius, radius, fill)
        fill.color = colorKey
        canvas.drawRoundRect(bubble, radius, radius, fill)
        label.textSize = 28 * sp
        label.typeface = if (key.isNewLetter) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        label.color = if (key.isNewLetter) colorNewLetter else colorInk
        drawCentered(canvas, labelOf(key), bubble.centerX(), bubble.centerY())
    }

    private fun drawCentered(canvas: Canvas, text: String, x: Float, y: Float) {
        val fm = label.fontMetrics
        canvas.drawText(text, x, y - (fm.ascent + fm.descent) / 2, label)
    }

    private fun labelOf(key: Key): String {
        key.text?.let { return if (upper && it.length == 1 && it[0].isLetter()) it.uppercase() else it }
        return when (key.action) {
            Action.SHIFT -> if (locked) "⇪" else "⇧"
            Action.BACKSPACE -> "⌫"
            Action.SYMBOLS -> "?123"
            Action.SYMBOLS2 -> "=\\<"
            Action.LETTERS -> lettersLabel
            Action.LANGUAGE -> "🌐"
            Action.SPACE -> spaceLabel
            Action.ENTER -> "↵"
            Action.EMOJI -> "😊"
            Action.EMOJI_PREV -> "◀"
            Action.EMOJI_NEXT -> "▶"
            null -> ""
        }
    }

    /**
     * Что произносит TalkBack. В полях паролей вместо букв — «символ», чтобы пароль не прозвучал
     * на всю комнату (как в системной клавиатуре Android); буквы называются только в наушниках.
     */
    private fun describe(key: Key): String {
        key.text?.let {
            if (sensitiveField && !privateAudio()) return context.getString(R.string.key_hidden)
            return if (it == Layouts.TUTUQ) context.getString(R.string.key_tutuq) else labelOf(key)
        }
        return context.getString(
            when (key.action) {
                Action.SHIFT -> R.string.key_shift
                Action.BACKSPACE -> R.string.key_backspace
                Action.SYMBOLS, Action.SYMBOLS2 -> R.string.key_symbols
                Action.LETTERS -> R.string.key_letters
                Action.LANGUAGE -> R.string.key_language
                Action.SPACE -> R.string.key_space
                Action.ENTER -> R.string.key_enter
                Action.EMOJI -> R.string.key_emoji
                Action.EMOJI_PREV -> R.string.key_emoji_prev
                Action.EMOJI_NEXT, null -> R.string.key_emoji_next
            }
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val id = e.getPointerId(i)
                val key = keyRects.getOrNull(indexAt(e.getX(i), e.getY(i)))?.first ?: return true
                pressed[id] = key
                feedback(key)
                if (preview && key.text != null) previewKey = key
                scheduleHold(id, key)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> release(e.getPointerId(e.actionIndex), commit = true)
            MotionEvent.ACTION_CANCEL -> pressed.keys.toList().forEach { release(it, commit = false) }
        }
        return true
    }

    private fun feedback(key: Key) {
        if (haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (sound) {
            audio.playSoundEffect(
                when (key.action) {
                    Action.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                    Action.BACKSPACE -> AudioManager.FX_KEYPRESS_DELETE
                    Action.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                    else -> AudioManager.FX_KEYPRESS_STANDARD
                },
                -1f,
            )
        }
    }

    private fun indexAt(x: Float, y: Float): Int =
        keyRects.indices.minByOrNull { i ->
            val r = keyRects[i].second
            val dx = if (x < r.left) r.left - x else if (x > r.right) x - r.right else 0f
            val dy = if (y < r.top) r.top - y else if (y > r.bottom) y - r.bottom else 0f
            hypot(dx, dy)
        } ?: -1

    private fun scheduleHold(id: Int, key: Key) {
        val task = when {
            key.action == Action.BACKSPACE -> object : Runnable {
                override fun run() {
                    longFired += id
                    listener?.onKey(key, false)
                    handler.postDelayed(this, 50)
                }
            }
            key.longPress != null || key.action == Action.LANGUAGE -> Runnable {
                longFired += id
                previewKey = null
                listener?.onKey(key, true)
                invalidate()
            }
            else -> return
        }
        timers[id] = task
        handler.postDelayed(task, 400)
    }

    private fun release(id: Int, commit: Boolean) {
        timers.remove(id)?.let { handler.removeCallbacks(it) }
        val key = pressed.remove(id)
        if (key == previewKey) previewKey = null
        if (key != null && commit && id !in longFired) listener?.onKey(key, false)
        longFired -= id
        invalidate()
    }

    // --- TalkBack: каждая клавиша — отдельный элемент; ввод при отрыве пальца, как в системных клавиатурах.

    private val provider = object : AccessibilityNodeProvider() {
        override fun createAccessibilityNodeInfo(id: Int): AccessibilityNodeInfo? {
            val view = this@KeyboardView
            if (id == HOST_VIEW_ID) {
                @Suppress("DEPRECATION") val info = AccessibilityNodeInfo.obtain(view)
                onInitializeAccessibilityNodeInfo(info)
                keyRects.indices.forEach { info.addChild(view, it) }
                return info
            }
            val (key, r) = keyRects.getOrNull(id) ?: return null
            @Suppress("DEPRECATION") val info = AccessibilityNodeInfo.obtain(view, id)
            info.packageName = context.packageName
            info.className = "android.inputmethodservice.Keyboard\$Key"
            info.contentDescription = describe(key)
            info.setParent(view)
            info.setSource(view, id)
            val bounds = Rect().also { r.roundOut(it) }
            @Suppress("DEPRECATION") info.setBoundsInParent(bounds)
            val loc = IntArray(2).also { getLocationOnScreen(it) }
            bounds.offset(loc[0], loc[1])
            info.setBoundsInScreen(bounds)
            info.isEnabled = true
            info.isVisibleToUser = true
            info.isClickable = true
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
            if (id == a11yFocused) {
                info.isAccessibilityFocused = true
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
            } else {
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS)
            }
            return info
        }

        override fun performAction(id: Int, action: Int, arguments: Bundle?): Boolean {
            if (id == HOST_VIEW_ID) return performAccessibilityAction(action, arguments)
            val key = keyRects.getOrNull(id)?.first ?: return false
            return when (action) {
                AccessibilityNodeInfo.ACTION_CLICK -> {
                    listener?.onKey(key, false)
                    sendKeyEvent(id, AccessibilityEvent.TYPE_VIEW_CLICKED)
                    true
                }
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> {
                    a11yFocused = id
                    sendKeyEvent(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                    invalidate()
                    true
                }
                AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> {
                    if (a11yFocused == id) {
                        a11yFocused = -1
                        sendKeyEvent(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                    }
                    invalidate()
                    true
                }
                else -> false
            }
        }
    }

    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider = provider

    override fun dispatchHoverEvent(e: MotionEvent): Boolean {
        if (!a11y.isTouchExplorationEnabled) return super.dispatchHoverEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                val i = indexAt(e.x, e.y)
                if (i != hovered) {
                    if (hovered >= 0) sendKeyEvent(hovered, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
                    hovered = i
                    if (i >= 0) {
                        sendKeyEvent(i, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
                        provider.performAction(i, AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
                    }
                }
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                // Палец поднят над клавишей — вводим её. Ушёл за пределы клавиатуры — ничего не вводим.
                val inside = e.x in 0f..width.toFloat() && e.y in 0f..height.toFloat()
                if (hovered >= 0 && inside) {
                    val key = keyRects[hovered].first
                    feedback(key)
                    listener?.onKey(key, false)
                }
                if (hovered >= 0) sendKeyEvent(hovered, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
                hovered = -1
            }
        }
        return true
    }

    private fun sendKeyEvent(id: Int, type: Int) {
        if (!a11y.isEnabled) return
        val key = keyRects.getOrNull(id)?.first ?: return
        @Suppress("DEPRECATION") val event = AccessibilityEvent.obtain(type)
        event.packageName = context.packageName
        event.className = "android.inputmethodservice.Keyboard\$Key"
        event.contentDescription = describe(key)
        event.setSource(this, id)
        parent?.requestSendAccessibilityEvent(this, event)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        timers.clear()
        pressed.clear()
        longFired.clear()
        previewKey = null
        super.onDetachedFromWindow()
    }
}
