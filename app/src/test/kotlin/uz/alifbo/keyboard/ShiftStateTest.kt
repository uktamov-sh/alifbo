package uz.alifbo.keyboard

import org.junit.Test
import kotlin.test.assertEquals

class ShiftStateTest {

    @Test
    fun `одно нажатие включает заглавную на одну букву`() {
        val s = ShiftState()
        s.tap(1_000)
        assertEquals(ShiftState.Mode.ONCE, s.mode)
        s.afterLetter()
        assertEquals(ShiftState.Mode.OFF, s.mode)
    }

    @Test
    fun `двойное нажатие включает Caps Lock, который не сбрасывается буквами`() {
        val s = ShiftState()
        s.tap(1_000)
        s.tap(1_200)
        assertEquals(ShiftState.Mode.LOCK, s.mode)
        s.afterLetter()
        s.setAuto(false)
        assertEquals(ShiftState.Mode.LOCK, s.mode)
        s.tap(5_000)
        assertEquals(ShiftState.Mode.OFF, s.mode)
    }

    @Test
    fun `медленные нажатия просто переключают`() {
        val s = ShiftState()
        s.tap(1_000)
        s.tap(2_000)
        assertEquals(ShiftState.Mode.OFF, s.mode)
    }

    @Test
    fun `после Caps Lock третье быстрое нажатие не включает его снова`() {
        val s = ShiftState()
        s.tap(1_000)
        s.tap(1_100)
        s.tap(1_200)
        assertEquals(ShiftState.Mode.OFF, s.mode)
        s.tap(1_300)
        assertEquals(ShiftState.Mode.ONCE, s.mode)
    }

    @Test
    fun `автоматическая заглавная в начале предложения`() {
        val s = ShiftState()
        s.setAuto(true)
        assertEquals(ShiftState.Mode.ONCE, s.mode)
        s.setAuto(false)
        assertEquals(ShiftState.Mode.OFF, s.mode)
    }
}
