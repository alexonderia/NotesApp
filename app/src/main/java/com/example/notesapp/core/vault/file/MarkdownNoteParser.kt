package com.example.notesapp.core.vault.file

/**
 * Parses and serialises note files in Obsidian-style Markdown format.
 *
 * New body format (after `# Title` heading):
 *
 * ```
 * <!-- recognized-text:start -->
 * …
 * <!-- recognized-text:end -->
 *
 * <!-- manual-text:start -->
 * …
 * <!-- manual-text:end -->
 * ```
 *
 * Notes saved before this format: entire legacy body is treated as [NoteFileData.recognizedText].
 */
object MarkdownNoteParser {

    const val RECOGNIZED_TEXT_START = "<!-- recognized-text:start -->"
    const val RECOGNIZED_TEXT_END = "<!-- recognized-text:end -->"
    const val MANUAL_TEXT_START = "<!-- manual-text:start -->"
    const val MANUAL_TEXT_END = "<!-- manual-text:end -->"

    data class NoteFileData(
        val id: String,
        val title: String,
        val createdAt: Long,
        val updatedAt: Long,
        val folderId: String?,
        val folderName: String?,
        val recognizedText: String,
        val manualText: String,
        val recognizedStrokeIds: Set<String> = emptySet(),
    )

    // ── Parsing ──────────────────────────────────────────────────────────────

    fun parse(content: String): NoteFileData? {
        val lines = content.lines()
        if (lines.firstOrNull()?.trim() != "---") return null

        var secondDashIdx = -1
        for (i in 1 until lines.size) {
            if (lines[i].trim() == "---") {
                secondDashIdx = i
                break
            }
        }
        if (secondDashIdx == -1) return null

        val frontMatterLines = lines.subList(1, secondDashIdx)
        val rawBodyLines = if (secondDashIdx + 1 < lines.size) {
            lines.subList(secondDashIdx + 1, lines.size)
        } else emptyList()

        val fm = parseFrontMatter(frontMatterLines)
        val id = fm["id"] ?: return null
        val title = fm["title"] ?: ""
        val createdAt = fm["createdAt"]?.toLongOrNull() ?: System.currentTimeMillis()
        val updatedAt = fm["updatedAt"]?.toLongOrNull() ?: System.currentTimeMillis()
        val folderId = fm["folderId"]?.takeIf { it.isNotEmpty() }
        val folderName = fm["folderName"]?.takeIf { it.isNotEmpty() }

        val bodyTrimmed = rawBodyLines.dropWhile { it.isBlank() }
        val body = if (bodyTrimmed.firstOrNull()?.trimStart()?.startsWith("#") == true) {
            bodyTrimmed.drop(1).dropWhile { it.isBlank() }.joinToString("\n").trimEnd()
        } else {
            bodyTrimmed.joinToString("\n").trimEnd()
        }

        val (recognizedText, manualText) = splitRecognizedAndManual(body)
        val recognizedStrokeIds = parseRecognizedStrokeIds(fm["recognizedStrokeIds"])

        return NoteFileData(
            id = id,
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt,
            folderId = folderId,
            folderName = folderName,
            recognizedText = recognizedText,
            manualText = manualText,
            recognizedStrokeIds = recognizedStrokeIds,
        )
    }

    /**
     * Если маркеров нет — весь body считается распознанным текстом (старые файлы).
     */
    fun splitRecognizedAndManual(body: String): Pair<String, String> {
        if (!body.contains(RECOGNIZED_TEXT_START) || !body.contains(RECOGNIZED_TEXT_END)) {
            return body to ""
        }
        val recognized = extractBetweenMarkers(body, RECOGNIZED_TEXT_START, RECOGNIZED_TEXT_END)
            ?: return body to ""
        val manual = if (body.contains(MANUAL_TEXT_START) && body.contains(MANUAL_TEXT_END)) {
            extractBetweenMarkers(body, MANUAL_TEXT_START, MANUAL_TEXT_END) ?: ""
        } else {
            ""
        }
        return recognized to manual
    }

    private fun extractBetweenMarkers(source: String, startMarker: String, endMarker: String): String? {
        val startIdx = source.indexOf(startMarker)
        if (startIdx < 0) return null
        val afterStart = startIdx + startMarker.length
        val endIdx = source.indexOf(endMarker, afterStart)
        if (endIdx < 0) return null
        return source.substring(afterStart, endIdx).trimStart('\n', '\r').trimEnd()
    }

    /** Старые файлы без ключа читаются как [emptySet]. */
    internal fun parseRecognizedStrokeIds(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun parseFrontMatter(lines: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (line in lines) {
            val colonIdx = line.indexOf(':')
            if (colonIdx < 0) continue
            val key = line.substring(0, colonIdx).trim()
            val rawValue = line.substring(colonIdx + 1).trim()
            val value = if (rawValue.startsWith('"') && rawValue.endsWith('"') && rawValue.length >= 2) {
                rawValue.substring(1, rawValue.length - 1).replace("\\\"", "\"")
            } else {
                rawValue
            }
            result[key] = value
        }
        return result
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    fun serialize(
        id: String,
        title: String,
        createdAt: Long,
        updatedAt: Long,
        folderId: String?,
        folderName: String?,
        recognizedText: String,
        manualText: String,
        recognizedStrokeIds: Set<String> = emptySet(),
    ): String = buildString {
        appendLine("---")
        appendLine("id: ${escape(id)}")
        appendLine("title: ${escape(title)}")
        appendLine("createdAt: $createdAt")
        appendLine("updatedAt: $updatedAt")
        appendLine("recognizedStrokeIds: ${escape(recognizedStrokeIds.joinToString(","))}")
        if (folderId != null) {
            appendLine("folderId: ${escape(folderId)}")
            appendLine("folderName: ${escape(folderName ?: "")}")
        }
        appendLine("---")
        appendLine()
        appendLine("# $title")
        appendLine()
        appendLine(RECOGNIZED_TEXT_START)
        append(recognizedText.trimEnd())
        appendLine()
        appendLine(RECOGNIZED_TEXT_END)
        appendLine()
        appendLine(MANUAL_TEXT_START)
        append(manualText.trimEnd())
        appendLine()
        appendLine(MANUAL_TEXT_END)
    }

    private fun escape(value: String) = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
