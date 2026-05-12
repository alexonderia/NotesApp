package com.example.notesapp.core.recognition

import java.lang.Character

/**
 * Выбор между результатами моделей ru и en в режиме [RecognitionLanguageMode.RussianEnglishAuto].
 *
 * Старая логика «любая кириллица в ответе ru → всегда ru» ломала латиницу: русская модель часто даёт
 * 1–2 случайные кириллические буквы на английском слове, и тогда выбирался мусор вместо en.
 */
internal fun pickBestRussianEnglishAuto(ruResult: String, enResult: String): String {
    val ru = ruResult.trim()
    val en = enResult.trim()
    if (ru.isEmpty()) return en
    if (en.isEmpty()) return ru

    val ruCyr = ru.countUnicodeScript(Character.UnicodeScript.CYRILLIC)
    val enCyr = en.countUnicodeScript(Character.UnicodeScript.CYRILLIC)
    val ruLat = ru.countUnicodeScript(Character.UnicodeScript.LATIN)
    val enLat = en.countUnicodeScript(Character.UnicodeScript.LATIN)

    val confRu = ruCyr * 15 + ruLat * 2
    val confEn = enLat * 8 + enCyr * 3

    return when {
        confRu > confEn + 3 -> ru
        confEn > confRu + 3 -> en
        ruCyr > enCyr -> ru
        enCyr > ruCyr -> en
        ruCyr >= 2 -> ru
        ruCyr <= 1 && enCyr <= 1 && enLat >= ruLat -> en
        en.length > ru.length -> en
        else -> ru
    }
}

private fun String.countUnicodeScript(script: Character.UnicodeScript): Int =
    count { Character.UnicodeScript.of(it.code) == script }

/** Среди вариантов от русской модели — кандидат с максимумом кириллицы (часто ближе к истине для RU). */
internal fun pickRepresentativeRuCandidate(candidates: List<String>): String {
    val list = candidates.asSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(8).toList()
    if (list.isEmpty()) return ""
    return list.maxWithOrNull(
        compareByDescending<String> { it.countUnicodeScript(Character.UnicodeScript.CYRILLIC) }
            .thenByDescending { it.length },
    ) ?: list.first()
}

/** Среди вариантов от английской модели — максимум латиницы, минимум кириллического «шума». */
internal fun pickRepresentativeEnCandidate(candidates: List<String>): String {
    val list = candidates.asSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(8).toList()
    if (list.isEmpty()) return ""
    return list.maxWithOrNull(
        compareByDescending<String> { it.countUnicodeScript(Character.UnicodeScript.LATIN) }
            .thenBy { it.countUnicodeScript(Character.UnicodeScript.CYRILLIC) }
            .thenByDescending { it.length },
    ) ?: list.first()
}
