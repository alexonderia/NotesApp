package com.example.notesapp.core.recognition

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPostProcessorTest {

    @Test
    fun fixesFrequentSplitWordsFromInk() {
        val raw = "Чтобы приготовить пирог, ну жно много и вишн я."
        val out = TextPostProcessor.cleanBlockText(raw)
        assertTrue(out.contains("нужно"))
        assertTrue(out.contains("вишня"))
        assertFalse(out.contains("ну жно"))
        assertFalse(out.contains("вишн я"))
    }
}
