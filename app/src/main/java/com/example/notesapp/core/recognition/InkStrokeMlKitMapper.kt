package com.example.notesapp.core.recognition

import com.example.notesapp.core.model.InkStroke
import com.google.mlkit.vision.digitalink.Ink

fun List<InkStroke>.toMlKitInk(): Ink {
    val inkBuilder = Ink.builder()
    for (stroke in this) {
        val strokeBuilder = Ink.Stroke.builder()
        for (point in stroke.points) {
            strokeBuilder.addPoint(Ink.Point.create(point.x, point.y, point.timestamp))
        }
        inkBuilder.addStroke(strokeBuilder.build())
    }
    return inkBuilder.build()
}
