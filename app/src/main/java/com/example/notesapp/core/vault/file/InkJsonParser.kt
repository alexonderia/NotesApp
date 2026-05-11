package com.example.notesapp.core.vault.file

import android.util.Log
import com.example.notesapp.core.model.InkStroke
import com.example.notesapp.core.model.StrokePoint
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Reads and writes `.ink.json` files. Uses [org.json] (part of Android SDK, no extra dep). */
object InkJsonParser {

    private const val TAG = "InkJsonParser"

    fun parse(json: String): List<InkStroke> = try {
        val obj = JSONObject(json)
        val strokesArr = obj.optJSONArray("strokes") ?: return emptyList()
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
            )
        }
    } catch (e: Exception) {
        Log.w(TAG, "parse failed: ${e.message}")
        emptyList()
    }

    fun serialize(noteId: String, strokes: List<InkStroke>): String {
        val obj = JSONObject()
        obj.put("noteId", noteId)
        obj.put("version", 1)
        val strokesArr = JSONArray()
        for (stroke in strokes) {
            val s = JSONObject()
            s.put("id", stroke.id)
            s.put("color", stroke.color)
            s.put("width", stroke.width.toDouble())
            s.put("timestamp", stroke.timestamp)
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
            strokesArr.put(s)
        }
        obj.put("strokes", strokesArr)
        return obj.toString(2)
    }
}
