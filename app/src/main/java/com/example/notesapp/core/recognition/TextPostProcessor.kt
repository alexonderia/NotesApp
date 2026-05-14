package com.example.notesapp.core.recognition

/**
 * Лёгкая постобработка текста после ML Kit: пробелы и пунктуация без «умного» NLP.
 */
object TextPostProcessor {

    private val horizontalWhitespace = Regex("""[\t ]+""")
    private val spaceBeforePunctuation = Regex(""" +([.,:;!?)\]])""")
    /** Буква в любой поддерживаемой локали (латиница, кириллица и т.д.). */
    private val punctuationThenLetter = Regex("""([.,:;!?)\]])(\p{L})""")

    /** Типичные разрывы слова пробелом после печатного рукописного ввода (см. Digital Ink + кириллица). */
    private val ruInkLetterSplits = listOf(
        Regex("""(?iu)ну\s+жно""") to "нужно",
        Regex("""(?iu)вишн\s+я""") to "вишня",
    )

    fun cleanBlockText(raw: String): String {
        val normalized = raw.trim().replace("\r\n", "\n")
        if (normalized.isEmpty()) return ""

        val lines = normalized.split('\n').map { cleanSingleLine(it) }
        val collapsed = collapseBlankLineRuns(lines)
        return trimOuterEmptyLines(collapsed).joinToString("\n").trimEnd()
    }

    fun assembleBlocks(blockTexts: List<String>): String =
        blockTexts
            .asSequence()
            .map { cleanBlockText(it) }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    private fun cleanSingleLine(line: String): String {
        var s = line.trim()
        if (s.isEmpty()) return ""
        for ((pattern, replacement) in ruInkLetterSplits) {
            s = pattern.replace(s, replacement)
        }
        s = horizontalWhitespace.replace(s, " ")
        while (true) {
            val next = spaceBeforePunctuation.replace(s, "$1")
            if (next == s) break
            s = next
        }
        s = punctuationThenLetter.replace(s, "$1 $2")
        s = horizontalWhitespace.replace(s, " ").trim()
        return s
    }

    /**
     * Сохраняет не более одной пустой строки подряд (осмысленный разрыв абзаца),
     * убирает длинные «лестницы» из пустых строк.
     */
    private fun collapseBlankLineRuns(lines: List<String>): List<String> {
        val out = ArrayList<String>(lines.size)
        var consecutiveBlanks = 0
        for (line in lines) {
            if (line.isEmpty()) {
                consecutiveBlanks++
                if (consecutiveBlanks <= 1) {
                    out.add("")
                }
            } else {
                consecutiveBlanks = 0
                out.add(line)
            }
        }
        return out
    }

    private fun trimOuterEmptyLines(lines: List<String>): List<String> {
        if (lines.isEmpty()) return lines
        var start = 0
        var end = lines.size
        while (start < end && lines[start].isEmpty()) start++
        while (end > start && lines[end - 1].isEmpty()) end--
        if (start == 0 && end == lines.size) return lines
        return lines.subList(start, end)
    }
}
