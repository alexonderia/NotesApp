package com.example.notesapp.core.vault.file

import android.util.Log
import com.example.notesapp.core.model.Folder
import org.json.JSONArray
import org.json.JSONObject

/** Reads and writes `metadata/folders.json`. */
object FolderMetadataParser {

    private const val TAG = "FolderMetadataParser"

    data class FolderMeta(
        val id: String,
        val name: String,
        val createdAt: Long,
        val updatedAt: Long,
    )

    fun parse(json: String): List<FolderMeta> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val obj = arr.getJSONObject(i)
            val id = obj.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val name = obj.optString("name").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            FolderMeta(
                id = id,
                name = name,
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
            )
        }
    } catch (e: Exception) {
        Log.w(TAG, "parse failed: ${e.message}")
        emptyList()
    }

    fun serialize(folders: List<FolderMeta>): String {
        val arr = JSONArray()
        for (f in folders) {
            val obj = JSONObject()
            obj.put("id", f.id)
            obj.put("name", f.name)
            obj.put("createdAt", f.createdAt)
            obj.put("updatedAt", f.updatedAt)
            arr.put(obj)
        }
        return arr.toString(2)
    }

    fun toFolder(meta: FolderMeta) = Folder(
        id = meta.id,
        name = meta.name,
        createdAt = meta.createdAt,
        updatedAt = meta.updatedAt,
    )

    fun fromFolder(folder: Folder) = FolderMeta(
        id = folder.id,
        name = folder.name,
        createdAt = folder.createdAt,
        updatedAt = folder.updatedAt,
    )
}
