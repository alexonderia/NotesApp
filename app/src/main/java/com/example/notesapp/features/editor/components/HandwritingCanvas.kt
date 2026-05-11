package com.example.notesapp.features.editor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.notesapp.core.model.InkStroke
import com.example.notesapp.core.model.StrokePoint

@Composable
fun HandwritingCanvas(
    strokes: List<InkStroke>,
    onStrokeFinished: (InkStroke) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val canvasBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    val currentPoints = remember { mutableStateListOf<StrokePoint>() }
    var isDrawing by remember { mutableStateOf(false) }

    Canvas(
        modifier = modifier
            .clip(shape)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDrawing = true
                        currentPoints.clear()
                        currentPoints.add(
                            StrokePoint(
                                x = offset.x,
                                y = offset.y,
                                pressure = 1f,
                                timestamp = System.currentTimeMillis(),
                            ),
                        )
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentPoints.add(
                            StrokePoint(
                                x = change.position.x,
                                y = change.position.y,
                                pressure = if (change.pressure > 0f) change.pressure else 1f,
                                timestamp = System.currentTimeMillis(),
                            ),
                        )
                    },
                    onDragEnd = {
                        if (currentPoints.isNotEmpty()) {
                            onStrokeFinished(
                                InkStroke(
                                    points = currentPoints.toList(),
                                    timestamp = System.currentTimeMillis(),
                                ),
                            )
                        }
                        currentPoints.clear()
                        isDrawing = false
                    },
                    onDragCancel = {
                        currentPoints.clear()
                        isDrawing = false
                    },
                )
            },
    ) {
        drawRect(color = canvasBackground, size = size)

        for (stroke in strokes) {
            drawStroke(stroke.points, stroke.color, stroke.width)
        }

        if (currentPoints.size >= 2) {
            drawStroke(currentPoints, 0xFF000000L, 4f)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(
    points: List<StrokePoint>,
    colorLong: Long,
    width: Float,
) {
    if (points.size < 2) return
    val color = Color(colorLong)
    for (i in 0 until points.size - 1) {
        drawLine(
            color = color,
            start = Offset(points[i].x, points[i].y),
            end = Offset(points[i + 1].x, points[i + 1].y),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }
}
