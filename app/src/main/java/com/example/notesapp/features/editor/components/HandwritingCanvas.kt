package com.example.notesapp.features.editor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.notesapp.core.model.InkStroke
import com.example.notesapp.core.model.StrokePoint
import com.example.notesapp.core.model.ToolType
import kotlin.math.hypot

@Composable
fun HandwritingCanvas(
    strokes: List<InkStroke>,
    selectedTool: ToolType,
    penColor: Long,
    penWidth: Float,
    eraserRadius: Float,
    onPenStrokeFinished: (InkStroke) -> Unit,
    onStrokesReplace: (List<InkStroke>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val canvasBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    val currentPoints = remember { mutableStateListOf<StrokePoint>() }

    Canvas(
        modifier = modifier
            .clip(shape)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .pointerInput(selectedTool, strokes, penColor, penWidth, eraserRadius) {
                detectDragGestures(
                    onDragStart = { offset ->
                        currentPoints.clear()
                        if (selectedTool == ToolType.Pen) {
                            currentPoints.add(
                                StrokePoint(
                                    x = offset.x,
                                    y = offset.y,
                                    pressure = 1f,
                                    timestamp = System.currentTimeMillis(),
                                ),
                            )
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        when (selectedTool) {
                            ToolType.Pen -> {
                                currentPoints.add(
                                    StrokePoint(
                                        x = change.position.x,
                                        y = change.position.y,
                                        pressure = if (change.pressure > 0f) change.pressure else 1f,
                                        timestamp = System.currentTimeMillis(),
                                    ),
                                )
                            }
                            ToolType.Eraser -> {
                                val prev = change.previousPosition
                                val curr = change.position
                                val hitIds = strokes
                                    .filter { it.toolType == ToolType.Pen }
                                    .filter { stroke ->
                                        strokeHitByEraserSegment(
                                            stroke = stroke,
                                            ax = prev.x,
                                            ay = prev.y,
                                            bx = curr.x,
                                            by = curr.y,
                                            radius = eraserRadius,
                                        )
                                    }
                                    .map { it.id }
                                    .toSet()
                                if (hitIds.isNotEmpty()) {
                                    val next = strokes.filter { it.id !in hitIds }
                                    if (next.size != strokes.size) {
                                        onStrokesReplace(next)
                                    }
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        if (selectedTool == ToolType.Pen && currentPoints.size >= 2) {
                            onPenStrokeFinished(
                                InkStroke(
                                    points = currentPoints.toList(),
                                    color = penColor,
                                    width = penWidth,
                                    timestamp = System.currentTimeMillis(),
                                    toolType = ToolType.Pen,
                                ),
                            )
                        }
                        currentPoints.clear()
                    },
                    onDragCancel = {
                        currentPoints.clear()
                    },
                )
            },
    ) {
        drawRect(color = canvasBackground, size = size)

        for (stroke in strokes) {
            if (stroke.points.size >= 2) {
                drawStroke(stroke.points, stroke.color, stroke.width)
            } else if (stroke.points.size == 1) {
                val p = stroke.points[0]
                drawCircle(
                    color = Color(stroke.color),
                    radius = stroke.width / 2f,
                    center = Offset(p.x, p.y),
                )
            }
        }

        if (selectedTool == ToolType.Pen && currentPoints.size >= 2) {
            drawStroke(currentPoints, penColor, penWidth)
        }
    }
}

private fun strokeHitByEraserSegment(
    stroke: InkStroke,
    ax: Float,
    ay: Float,
    bx: Float,
    by: Float,
    radius: Float,
): Boolean {
    val tol = radius + stroke.width * 0.5f + 6f
    val pts = stroke.points
    if (pts.isEmpty()) return false
    if (pts.size == 1) {
        return minDistancePointToSegment(pts[0].x, pts[0].y, ax, ay, bx, by) <= tol
    }
    val samples = 10
    for (i in 0..samples) {
        val t = i.toFloat() / samples
        val px = ax + (bx - ax) * t
        val py = ay + (by - ay) * t
        if (minDistancePointToPolyline(px, py, pts) <= tol) return true
    }
    for (i in 0 until pts.size - 1) {
        if (segmentToSegmentMinDistance(
                ax, ay, bx, by,
                pts[i].x, pts[i].y, pts[i + 1].x, pts[i + 1].y,
            ) <= tol
        ) {
            return true
        }
    }
    return false
}

private fun minDistancePointToPolyline(px: Float, py: Float, pts: List<StrokePoint>): Float {
    if (pts.size == 1) return hypot(px - pts[0].x, py - pts[0].y)
    var d = Float.MAX_VALUE
    for (i in 0 until pts.size - 1) {
        d = minOf(d, distPointToSegment(px, py, pts[i].x, pts[i].y, pts[i + 1].x, pts[i + 1].y))
    }
    return d
}

private fun minDistancePointToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float =
    distPointToSegment(px, py, ax, ay, bx, by)

private fun distPointToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    val lenSq = dx * dx + dy * dy
    if (lenSq < 1e-4f) return hypot(px - x1, py - y1)
    val t = (((px - x1) * dx + (py - y1) * dy) / lenSq).coerceIn(0f, 1f)
    val qx = x1 + t * dx
    val qy = y1 + t * dy
    return hypot(px - qx, py - qy)
}

/** Минимальное расстояние между отрезками [a1,a2] и [b1,b2] (упрощённо: дискретизация). */
private fun segmentToSegmentMinDistance(
    ax: Float,
    ay: Float,
    bx: Float,
    by: Float,
    cx: Float,
    cy: Float,
    dx: Float,
    dy: Float,
): Float {
    var m = Float.MAX_VALUE
    val steps = 6
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val px = ax + (bx - ax) * t
        val py = ay + (by - ay) * t
        m = minOf(m, distPointToSegment(px, py, cx, cy, dx, dy))
    }
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val px = cx + (dx - cx) * t
        val py = cy + (dy - cy) * t
        m = minOf(m, distPointToSegment(px, py, ax, ay, bx, by))
    }
    return m
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
