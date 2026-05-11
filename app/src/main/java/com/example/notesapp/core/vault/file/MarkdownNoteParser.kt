package com.example.notesapp.core.vault.file

/**
 * Parses and serialises note files in Obsidian-style Markdown format:
 *
 * ```
 * ---
 * id: "note_..."
 * title: "My Note"
 * createdAt: 1715400000000
 * updatedAt: 1715401200000
 * folderId: "folder_..."
 * folderName: "Study"
 * ---
 *
 * # My Note
 *
 * Body text…
 * ```
 *
 * The `# Title` heading is written on every save and stripped on read so that
 * [NoteFileData.body] contains only the user-authored content.
 */
object MarkdownNoteParser {

    data class NoteFileData(
        val id: String,
        val title: String,
        val createdAt: Long,
        val updatedAt: Long,
        val folderId: String?,
        val folderName: String?,
        val body: String,
    )

    // ── Parsing ──────────────────────────────────────────────────────────────

    fun parse(content: String): NoteFileData? {
        val lines = content.lines()
        if (lines.firstOrNull()?.trim() != "---") return null

        // Find the closing "---"
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

        // Strip leading blank lines then the "# heading" line (if any)
        val bodyTrimmed = rawBodyLines.dropWhile { it.isBlank() }
        val body = if (bodyTrimmed.firstOrNull()?.trimStart()?.startsWith("#") == true) {
            bodyTrimmed.drop(1).dropWhile { it.isBlank() }.joinToString("\n").trimEnd()
        } else {
            bodyTrimmed.joinToString("\n").trimEnd()
        }

        return NoteFileData(
            id = id,
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt,
            folderId = folderId,
            folderName = folderName,
            body = body,
        )
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
        body: String,
    ): String = buildString {
        appendLine("---")
        appendLine("id: ${escape(id)}")
        appendLine("title: ${escape(title)}")
        appendLine("createdAt: $createdAt")
        appendLine("updatedAt: $updatedAt")
        if (folderId != null) {
            appendLine("folderId: ${escape(folderId)}")
            appendLine("folderName: ${escape(folderName ?: "")}")
        }
        appendLine("---")
        appendLine()
        appendLine("# $title")
        if (body.isNotBlank()) {
            appendLine()
            append(body.trimEnd())
        }
    }

    private fun escape(value: String) = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
