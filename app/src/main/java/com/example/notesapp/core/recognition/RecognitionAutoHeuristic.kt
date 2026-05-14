package com.example.notesapp.core.recognition

import java.lang.Character

/**
 * Выбор между результатами моделей ru и en в режиме [RecognitionLanguageMode.RussianEnglishAuto].
 *
 * Старая логика «любая кириллица в ответе ru → всегда ru» ломала латиницу: русская модель часто даёт
 * 1–2 случайные кириллические буквы на английском слове, и тогда выбирался мусор вместо en.
 *
 * **Пропись:** английская модель по тем же штрихам часто выдаёт длинный транслит и «выигрывает» по
 * числовым оценкам — тогда выбирается мусор вместо читаемой кириллицы из ru. Поэтому при явном
 * преобладании кириллицы в ru и почти полном отсутствии кириллицы в en принудительно берём ru.
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

    // Длинная кириллическая фраза + «английский» ответ почти без кириллицы — почти всегда транслит.
    if (enCyr <= 2 && ruCyr >= 10) return ru

    // Сильное преимущество ru по кириллице при слабом кириллическом сигнале в en.
    if (ruCyr >= enCyr + 8 && enCyr <= 3 && ruCyr >= 8) return ru

    val confRu = ruCyr * 15 + ruLat * 2
    val confEn = enLat * 8 + enCyr * 3

    val nearTie = kotlin.math.abs(confRu - confEn) <= 10

    return when {
        confRu > confEn + 3 -> ru
        confEn > confRu + 3 -> en
        nearTie && (ruCyr >= 1 || enCyr >= 1) -> when {
            ruCyr > enCyr -> ru
            enCyr > ruCyr -> en
            ruCyr == 0 && enCyr == 0 && enLat >= ruLat -> en
            ru.length >= en.length -> ru
            else -> en
        }
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
