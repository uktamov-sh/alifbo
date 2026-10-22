package uz.alifbo.keyboard

/** Состояние Shift: одно нажатие — одна заглавная, двойное — Caps Lock. Время передаётся снаружи. */
class ShiftState(private val doubleTapMs: Long = 320) {

    enum class Mode { OFF, ONCE, LOCK }

    var mode = Mode.OFF
        private set

    private var lastTap: Long? = null

    val isUpper get() = mode != Mode.OFF

    fun tap(now: Long) {
        val quick = lastTap?.let { now - it < doubleTapMs } == true
        mode = when {
            mode == Mode.LOCK -> Mode.OFF
            mode == Mode.ONCE && quick -> Mode.LOCK
            mode == Mode.OFF -> Mode.ONCE
            else -> Mode.OFF
        }
        // После включения Caps Lock следующее нажатие не должно считаться вторым из пары.
        lastTap = if (mode == Mode.LOCK) null else now
    }

    fun afterLetter() {
        if (mode == Mode.ONCE) mode = Mode.OFF
    }

    /** Заглавная по правилам поля (начало предложения). Caps Lock не трогается. */
    fun setAuto(capitalize: Boolean) {
        if (mode != Mode.LOCK) mode = if (capitalize) Mode.ONCE else Mode.OFF
    }
}
