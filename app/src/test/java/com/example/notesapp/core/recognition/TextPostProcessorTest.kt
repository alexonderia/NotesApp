package com.example.notesapp.core.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPostProcessorTest {

    @Test
    fun cleanBlockText_trimsAndCollapsesSpaces() {
        assertEquals("a b c", TextPostProcessor.cleanBlockText("  a   b\tc  "))
    }

    @Test
    fun cleanBlockText_removesSpaceBeforePunctuation() {
        assertEquals("a, b", TextPostProcessor.cleanBlockText("a , b"))
        assertEquals("Слово.", TextPostProcessor.cleanBlockText("Слово ."))
    }

    @Test
    fun cleanBlockText_addsSpaceAfterPunctuationBeforeLetter() {
        assertEquals("Hello. World", TextPostProcessor.cleanBlockText("Hello.World"))
        assertEquals("Привет. Мир", TextPostProcessor.cleanBlockText("Привет.Мир"))
        assertEquals("One: Two", TextPostProcessor.cleanBlockText("One:Two"))
    }

    @Test
    fun cleanBlockText_doesNotInsertSpaceBetweenDotAndDigit() {
        assertEquals("v2.0", TextPostProcessor.cleanBlockText("v2.0"))
        assertEquals("v2.0", TextPostProcessor.cleanBlockText("v2 .0"))
    }

    @Test
    fun cleanBlockText_preservesPunctuationCharacters() {
        val s = TextPostProcessor.cleanBlockText("Тест: а, б; в! г? (д) [е]")
        assertTrue(s.contains(':'))
        assertTrue(s.contains(','))
        assertTrue(s.contains(';'))
        assertTrue(s.contains('!'))
        assertTrue(s.contains('?'))
        assertTrue(s.contains('('))
        assertTrue(s.contains(')'))
        assertTrue(s.contains('['))
        assertTrue(s.contains(']'))
    }

    @Test
    fun cleanBlockText_collapsesMultipleBlankLines() {
        assertEquals(
            "a\n\nb",
            TextPostProcessor.cleanBlockText("a\n\n\n\nb"),
        )
    }

    @Test
    fun cleanBlockText_trimsOuterEmptyLines() {
        assertEquals("x", TextPostProcessor.cleanBlockText("\n\nx\n\n"))
    }

    @Test
    fun assembleBlocks_dropsEmptyAndTrimsEdges() {
        assertEquals(
            "a\nc",
            TextPostProcessor.assembleBlocks(listOf("  a  ", "", "  ", "c")),
        )
    }

    @Test
    fun assembleBlocks_noTrailingBlankLine() {
        assertEquals("a", TextPostProcessor.assembleBlocks(listOf("a", "   ", "")))
    }
}
