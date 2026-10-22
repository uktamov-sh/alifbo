package uz.alifbo.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
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
    private val keyHeight = 50 * dp
    private val rowGap = 8 * dp
    private val keyGap = 5 * dp
    private val pad = 4 * dp
    private val radius = 7 * dp

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
        textSize = 10 * resources.displayMetrics.scaledDensity
        color = colorMuted
    }

    private val keyRects = ArrayList<Pair<Key, RectF>>()
    private val pressed = HashMap<Int, Key>()
    private val longFired = HashSet<Int>()
    private val timers = HashMap<Int, Runnable>()
    private val handler = Handler(Looper.getMainLooper())

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
        for ((key, r) in keyRects) {
            fill.color = colorShadow
            canvas.drawRoundRect(r.left, r.top + dp, r.right, r.bottom + dp, radius, radius, fill)
            fill.color = when {
                pressed.containsValue(key) -> colorPressed
                key.action == Action.ENTER -> colorAccent
                key.action == Action.SHIFT && upper -> colorAccent
                key.action != null && key.action != Action.SPACE -> colorSpecial
                else -> colorKey
            }
            canvas.drawRoundRect(r, radius, radius, fill)

            val text = labelOf(key)
            val size = when (key.action) {
                null, Action.SHIFT, Action.BACKSPACE, Action.ENTER -> 22
                Action.SPACE -> 13
                else -> 16
            }
            label.textSize = size * resources.displayMetrics.scaledDensity
            label.typeface = if (key.isNewLetter) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            label.color = when {
                key.action == Action.ENTER || (key.action == Action.SHIFT && upper) -> colorOnAccent
                key.isNewLetter -> colorNewLetter
                key.action == Action.SPACE -> colorMuted
                else -> colorInk
            }
            val fm = label.fontMetrics
            canvas.drawText(text, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, label)

            if (key.text != null && key.longPress != null) {
                canvas.drawText(key.longPress, r.right - 4 * dp, r.top + 12 * dp, hint)
            }
        }
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
            null -> ""
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val id = e.getPointerId(i)
                val key = keyAt(e.getX(i), e.getY(i)) ?: return true
                pressed[id] = key
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                scheduleHold(id, key)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> release(e.getPointerId(e.actionIndex), commit = true)
            MotionEvent.ACTION_CANCEL -> pressed.keys.toList().forEach { release(it, commit = false) }
        }
        return true
    }

    private fun keyAt(x: Float, y: Float): Key? =
        keyRects.minByOrNull { (_, r) ->
            val dx = if (x < r.left) r.left - x else if (x > r.right) x - r.right else 0f
            val dy = if (y < r.top) r.top - y else if (y > r.bottom) y - r.bottom else 0f
            hypot(dx, dy)
        }?.first

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
        if (key != null && commit && id !in longFired) listener?.onKey(key, false)
        longFired -= id
        invalidate()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        timers.clear()
        pressed.clear()
        longFired.clear()
        super.onDetachedFromWindow()
    }
}
