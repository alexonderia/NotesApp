package com.example.notesapp.core.recognition

import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionAutoHeuristicTest {

    @Test
    fun nearTieWithMixedScriptPrefersMoreCyrillic() {
        val ru = "аb"
        val en = "ab"
        assertEquals(ru, pickBestRussianEnglishAuto(ru, en))
    }

    @Test
    fun longCyrillicWithLatinTransliterationPicksRussian() {
        val ru = "Чтобы приготовить вишневый пирог нужно много ингредиентов"
        val en = "Chtoby prigotovit vishnevyj pirog nuzhno mnogo ingredientov"
        assertEquals(ru, pickBestRussianEnglishAuto(ru, en))
    }
}
