package com.example.notesapp.core.vault.file

import android.util.Log
import com.example.notesapp.core.model.HandwritingBlock
import com.example.notesapp.core.model.InkStroke
import com.example.notesapp.core.model.StrokeBounds
import com.example.notesapp.core.model.StrokePoint
import com.example.notesapp.core.model.ToolType
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Reads and writes `.ink.json` files. Uses [org.json] (part of Android SDK, no extra dep). */
object InkJsonParser {

    private const val TAG = "InkJsonParser"

    data class InkFileData(
        val strokes: List<InkStroke>,
        val handwritingBlocks: List<HandwritingBlock>,
    )

    fun parseInk(json: String): InkFileData = try {
        val obj = JSONObject(json)
        val strokesArr = obj.optJSONArray("strokes") ?: JSONArray()
        val strokes = parseStrokesArray(strokesArr)
        val blocksArr = obj.optJSONArray("handwritingBlocks")
        val blocks = if (blocksArr != null) parseBlocksArray(blocksArr) else emptyList()
        InkFileData(strokes = strokes, handwritingBlocks = blocks)
    } catch (e: Exception) {
        Log.w(TAG, "parseInk failed: ${e.message}")
        InkFileData(emptyList(), emptyList())
    }

    /** Обратная совместимость: только штрихи. */
    fun parse(json: String): List<InkStroke> = parseInk(json).strokes

    fun serialize(
        noteId: String,
        strokes: List<InkStroke>,
        handwritingBlocks: List<HandwritingBlock> = emptyList(),
    ): String {
        val obj = JSONObject()
        obj.put("noteId", noteId)
        obj.put("version", 2)
        val strokesArr = JSONArray()
        for (stroke in strokes) {
            strokesArr.put(strokeToJson(stroke))
        }
        obj.put("strokes", strokesArr)
        val blocksArr = JSONArray()
        for (block in handwritingBlocks) {
            blocksArr.put(blockToJson(block))
        }
        obj.put("handwritingBlocks", blocksArr)
        return obj.toString(2)
    }

    private fun parseStrokesArray(strokesArr: JSONArray): List<InkStroke> =
        (0 until strokesArr.length()).map { i ->
            val s = strokesArr.getJSONObject(i)
            val pointsArr = s.optJSONArray("points") ?: JSONArray()
            val points = (0 until pointsArr.length()).map { j ->
                val p = pointsArr.getJSONObject(j)
                StrokePoint(
                    x = p.getDouble("x").toFloat(),
                    y = p.getDouble("y").toFloat(),
                    pressure = p.optDouble("pressure", 1.0).toFloat(),
                    timestamp = p.optLong("timestamp", System.currentTimeMillis()),
                )
            }
            InkStroke(
                id = s.optString("id").ifEmpty { UUID.randomUUID().toString() },
                color = s.optLong("color", 0xFF000000L),
                width = s.optDouble("width", 4.0).toFloat(),
                timestamp = s.optLong("timestamp", System.currentTimeMillis()),
                points = points,
                toolType = parseToolType(s.optString("toolType")),
            )
        }

    private fun parseToolType(raw: String): ToolType =
        when (raw.lowercase()) {
            "eraser" -> ToolType.Eraser
            else -> ToolType.Pen
        }

    private fun parseBlocksArray(blocksArr: JSONArray): List<HandwritingBlock> =
        (0 until blocksArr.length()).mapNotNull { i ->
            try {
                val b = blocksArr.getJSONObject(i)
                val idsArr = b.optJSONArray("strokeIds") ?: JSONArray()
                val strokeIds = (0 until idsArr.length()).map { idx -> idsArr.getString(idx) }
                val boundsObj = b.getJSONObject("bounds")
                val bounds = StrokeBounds(
                    minX = boundsObj.getDouble("minX").toFloat(),
                    minY = boundsObj.getDouble("minY").toFloat(),
                    maxX = boundsObj.getDouble("maxX").toFloat(),
                    maxY = boundsObj.getDouble("maxY").toFloat(),
                )
                HandwritingBlock(
                    id = b.optString("id").ifEmpty { UUID.randomUUID().toString() },
                    strokeIds = strokeIds,
                    bounds = bounds,
                    recognizedText = b.optString("recognizedText", ""),
                    orderIndex = b.optInt("orderIndex", 0),
                    updatedAt = b.optLong("updatedAt", System.currentTimeMillis()),
                )
            } catch (e: Exception) {
                Log.w(TAG, "parse block failed: ${e.message}")
                null
            }
        }

    private fun strokeToJson(stroke: InkStroke): JSONObject {
        val s = JSONObject()
        s.put("id", stroke.id)
        s.put("color", stroke.color)
        s.put("width", stroke.width.toDouble())
        s.put("timestamp", stroke.timestamp)
        s.put("toolType", stroke.toolType.name.lowercase())
        val pointsArr = JSONArray()
        for (point in stroke.points) {
            val p = JSONObject()
            p.put("x", point.x.toDouble())
            p.put("y", point.y.toDouble())
            p.put("pressure", point.pressure.toDouble())
            p.put("timestamp", point.timestamp)
            pointsArr.put(p)
        }
        s.put("points", pointsArr)
        return s
    }

    private fun blockToJson(block: HandwritingBlock): JSONObject {
        val b = JSONObject()
        b.put("id", block.id)
        val idsArr = JSONArray()
        for (id in block.strokeIds) {
            idsArr.put(id)
        }
        b.put("strokeIds", idsArr)
        val bo = JSONObject()
        bo.put("minX", block.bounds.minX.toDouble())
        bo.put("minY", block.bounds.minY.toDouble())
        bo.put("maxX", block.bounds.maxX.toDouble())
        bo.put("maxY", block.bounds.maxY.toDouble())
        b.put("bounds", bo)
        b.put("recognizedText", block.recognizedText)
        b.put("orderIndex", block.orderIndex)
        b.put("updatedAt", block.updatedAt)
        return b
    }
}
