package dev.octoshrimpy.quik.common.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class AlphabetIndexTest {

    @Test
    fun scrollTargetMapsFractionToSection() {
        assertEquals(0, scrollTarget(0f, 25))
        assertEquals(13, scrollTarget(0.5f, 25))
        assertEquals(25, scrollTarget(1f, 25))
        assertEquals(0, scrollTarget(-1f, 25))
        assertEquals(25, scrollTarget(2f, 25))
        assertEquals(0, scrollTarget(0.5f, 0))
    }

    @Test
    fun bendFallsOffWithDistance() {
        assertEquals(1f, alphabetBend(0f), 0.0001f)
        assertEquals(0.5625f, alphabetBend(72f), 0.0001f)
        assertEquals(0f, alphabetBend(144f), 0.0001f)
        assertEquals(0f, alphabetBend(300f), 0.0001f)
    }

    @Test
    fun sectionNormalisesFirstLetter() {
        assertEquals("E", contactSection("Émile"))
        assertEquals("M", contactSection("  moez"))
        assertEquals("#", contactSection("1abc"))
        assertEquals("#", contactSection(""))
        assertEquals("#", contactSection(null))
    }
}
