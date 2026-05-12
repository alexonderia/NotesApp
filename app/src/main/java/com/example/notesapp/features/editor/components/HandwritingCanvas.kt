package com.example.notesapp.features.editor.components

import android.os.Build
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.RequestDisallowInterceptTouchEvent
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.dp
import com.example.notesapp.core.model.InkStroke
import com.example.notesapp.core.model.StrokeBounds
import com.example.notesapp.core.model.StrokePoint
import com.example.notesapp.core.model.ToolType
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private const val MinPointDistancePx = 1.75f
private val MinPointDistanceSq: Float get() = MinPointDistancePx * MinPointDistancePx

private const val EraserReplaceMinIntervalMs = 24L

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

    val strokesState = rememberUpdatedState(strokes)
    val onPenStrokeFinishedState = rememberUpdatedState(onPenStrokeFinished)
    val onStrokesReplaceState = rememberUpdatedState(onStrokesReplace)
    val penColorState = rememberUpdatedState(penColor)
    val penWidthState = rememberUpdatedState(penWidth)
    val toolState = rememberUpdatedState(selectedTool)
    val eraserRadiusState = rememberUpdatedState(eraserRadius)

    val currentStrokePoints = remember { ArrayList<StrokePoint>(256) }
    var drawInvalidateTick by remember { mutableIntStateOf(0) }

    val activePointerId = remember { intArrayOf(MotionEvent.INVALID_POINTER_ID) }
    val lastEraserPos = remember { FloatArray(2) } // prev x, y; initialized on eraser down
    var lastEraserReplaceUptimeMs by remember { mutableLongStateOf(0L) }
    var lastEraserReplaceSig by remember { mutableLongStateOf(Long.MIN_VALUE) }

    val disallowIntercept = remember { RequestDisallowInterceptTouchEvent() }
    val strokePath = remember { Path() }

    fun invalidateStrokeDraw() {
        drawInvalidateTick++
    }

    fun maybeNotifyStrokesReplace(next: List<InkStroke>) {
        val now = android.os.SystemClock.uptimeMillis()
        val sig = inkStrokesListSignature(next)
        if (sig == lastEraserReplaceSig && now - lastEraserReplaceUptimeMs < EraserReplaceMinIntervalMs) {
            return
        }
        lastEraserReplaceSig = sig
        lastEraserReplaceUptimeMs = now
        onStrokesReplaceState.value(next)
    }

    fun buildPenStrokeBoundsPairs(out: ArrayList<Pair<InkStroke, StrokeBounds>>) {
        out.clear()
        val s = strokesState.value
        for (i in s.indices) {
            val stroke = s[i]
            if (stroke.toolType == ToolType.Pen) {
                out.add(stroke to StrokeBounds.fromStroke(stroke))
            }
        }
    }

    val penStrokeBoundsScratch = remember { ArrayList<Pair<InkStroke, StrokeBounds>>(64) }

    fun appendEraserHitsForSegment(
        pairs: ArrayList<Pair<InkStroke, StrokeBounds>>,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        radius: Float,
        hitIds: MutableSet<String>,
    ) {
        for (j in pairs.indices) {
            val (stroke, bounds) = pairs[j]
            if (stroke.id in hitIds) continue
            if (!strokeMayBeHitByEraserSegment(bounds, stroke.width, ax, ay, bx, by, radius)) continue
            if (strokeHitByEraserSegment(stroke, ax, ay, bx, by, radius)) {
                hitIds.add(stroke.id)
            }
        }
    }

    fun applyEraserHits(hitIds: Set<String>) {
        if (hitIds.isEmpty()) return
        val current = strokesState.value
        val next = ArrayList<InkStroke>(max(8, current.size - hitIds.size))
        for (i in current.indices) {
            val st = current[i]
            if (st.id !in hitIds) next.add(st)
        }
        if (next.size == current.size) return
        maybeNotifyStrokesReplace(next)
    }

    fun appendPenPoint(x: Float, y: Float, pressure: Float, timestamp: Long) {
        val last = currentStrokePoints.lastOrNull()
        if (last != null) {
            val dx = x - last.x
            val dy = y - last.y
            if (dx * dx + dy * dy < MinPointDistanceSq) return
        }
        currentStrokePoints.add(
            StrokePoint(
                x = x,
                y = y,
                pressure = pressure,
                timestamp = timestamp,
            ),
        )
    }

    fun appendPenFromMotionIndex(event: MotionEvent, index: Int) {
        val historySize = event.historySize
        for (h in 0 until historySize) {
            val x = event.getHistoricalX(index, h)
            val y = event.getHistoricalY(index, h)
            val p = historicalPressureOrDefault(event, index, h)
            val t = historicalEventTimeMs(event, h)
            appendPenPoint(x, y, p, t)
        }
        val cx = event.getX(index)
        val cy = event.getY(index)
        val cp = currentPressureOrDefault(event, index)
        appendPenPoint(cx, cy, cp, event.eventTime)
    }

    fun finishPenStrokeIfNeeded() {
        if (toolState.value != ToolType.Pen) {
            currentStrokePoints.clear()
            invalidateStrokeDraw()
            return
        }
        val raw = ArrayList(currentStrokePoints)
        currentStrokePoints.clear()
        invalidateStrokeDraw()
        if (raw.size < 2) return
        val filtered = filterPointsMinDistance(raw, MinPointDistanceSq)
        val smoothed = smoothPoints(filtered)
        val finalPoints = if (smoothed.size >= 2) smoothed else filtered
        if (finalPoints.size < 2) return
        onPenStrokeFinishedState.value(
            InkStroke(
                points = finalPoints,
                color = penColorState.value,
                width = penWidthState.value,
                timestamp = System.currentTimeMillis(),
                toolType = ToolType.Pen,
            ),
        )
    }

    fun resetEraserDebounceState() {
        lastEraserReplaceSig = Long.MIN_VALUE
    }

    val shapeClip = shape
    Canvas(
        modifier = modifier
            .clip(shapeClip)
            .border(width = 1.dp, color = borderColor, shape = shapeClip)
            .pointerInteropFilter(
                requestDisallowInterceptTouchEvent = disallowIntercept,
            ) { event ->
                val tool = toolState.value
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        disallowIntercept(true)
                        val idx = event.actionIndex
                        activePointerId[0] = event.getPointerId(idx)
                        when (tool) {
                            ToolType.Pen -> {
                                currentStrokePoints.clear()
                                appendPenFromMotionIndex(event, idx)
                                invalidateStrokeDraw()
                            }
                            ToolType.Eraser -> {
                                resetEraserDebounceState()
                                lastEraserPos[0] = event.getX(idx)
                                lastEraserPos[1] = event.getY(idx)
                            }
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val pid = activePointerId[0]
                        val idx = if (pid != MotionEvent.INVALID_POINTER_ID) {
                            event.findPointerIndex(pid)
                        } else {
                            0
                        }
                        if (idx < 0) return@pointerInteropFilter true
                        when (tool) {
                            ToolType.Pen -> {
                                appendPenFromMotionIndex(event, idx)
                                invalidateStrokeDraw()
                            }
                            ToolType.Eraser -> {
                                buildPenStrokeBoundsPairs(penStrokeBoundsScratch)
                                val pairs = penStrokeBoundsScratch
                                val hitIds = mutableSetOf<String>()
                                val ax = lastEraserPos[0]
                                val ay = lastEraserPos[1]
                                val historySize = event.historySize
                                var px = ax
                                var py = ay
                                val rad = eraserRadiusState.value
                                for (h in 0 until historySize) {
                                    val hx = event.getHistoricalX(idx, h)
                                    val hy = event.getHistoricalY(idx, h)
                                    appendEraserHitsForSegment(pairs, px, py, hx, hy, rad, hitIds)
                                    px = hx
                                    py = hy
                                }
                                val cx = event.getX(idx)
                                val cy = event.getY(idx)
                                appendEraserHitsForSegment(pairs, px, py, cx, cy, rad, hitIds)
                                lastEraserPos[0] = cx
                                lastEraserPos[1] = cy
                                applyEraserHits(hitIds)
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        val pid = activePointerId[0]
                        val idx = if (pid != MotionEvent.INVALID_POINTER_ID) event.findPointerIndex(pid) else 0
                        if (idx >= 0 && tool == ToolType.Pen) {
                            appendPenFromMotionIndex(event, idx)
                            invalidateStrokeDraw()
                        }
                        if (tool == ToolType.Pen) {
                            finishPenStrokeIfNeeded()
                        }
                        activePointerId[0] = MotionEvent.INVALID_POINTER_ID
                        resetEraserDebounceState()
                    }
                    MotionEvent.ACTION_CANCEL,
                    MotionEvent.ACTION_OUTSIDE,
                    -> {
                        if (tool == ToolType.Pen) {
                            currentStrokePoints.clear()
                            invalidateStrokeDraw()
                        }
                        activePointerId[0] = MotionEvent.INVALID_POINTER_ID
                        resetEraserDebounceState()
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        val upIndex = event.actionIndex
                        val upId = event.getPointerId(upIndex)
                        if (upId == activePointerId[0]) {
                            if (tool == ToolType.Pen && upIndex >= 0) {
                                appendPenFromMotionIndex(event, upIndex)
                                invalidateStrokeDraw()
                                finishPenStrokeIfNeeded()
                            }
                            activePointerId[0] = MotionEvent.INVALID_POINTER_ID
                            resetEraserDebounceState()
                        }
                    }
                    else -> Unit
                }
                true
            },
    ) {
        @Suppress("UNUSED_VARIABLE")
        val drawEpoch = drawInvalidateTick

        drawRect(color = canvasBackground, size = size)

        val s = strokes
        for (i in s.indices) {
            val stroke = s[i]
            drawStrokePolyline(strokePath, stroke.points, stroke.color, stroke.width)
        }

        if (selectedTool == ToolType.Pen && currentStrokePoints.size >= 2) {
            drawStrokePolyline(strokePath, currentStrokePoints, penColor, penWidth)
        }
    }
}

private fun inkStrokesListSignature(list: List<InkStroke>): Long {
    var h = 0L
    for (i in list.indices) {
        h = h * 31L + list[i].id.hashCode().toLong()
    }
    return h xor (list.size.toLong() shl 32)
}

private fun historicalPressureOrDefault(event: MotionEvent, pointerIndex: Int, historicalPos: Int): Float {
    if (Build.VERSION.SDK_INT >= 29) {
        val p = event.getHistoricalPressure(pointerIndex, historicalPos)
        return if (p > 0f) p else 1f
    }
    return 1f
}

private fun currentPressureOrDefault(event: MotionEvent, pointerIndex: Int): Float {
    val p = try {
        event.getPressure(pointerIndex)
    } catch (_: Exception) {
        1f
    }
    return if (p > 0f) p else 1f
}

private fun historicalEventTimeMs(event: MotionEvent, historicalPos: Int): Long {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        event.getHistoricalEventTime(historicalPos)
    } else {
        event.eventTime
    }
}

private fun filterPointsMinDistance(points: List<StrokePoint>, minDistSq: Float): List<StrokePoint> {
    if (points.size <= 1) return points
    val out = ArrayList<StrokePoint>(points.size)
    out.add(points.first())
    var last = points.first()
    for (i in 1 until points.size) {
        val p = points[i]
        val dx = p.x - last.x
        val dy = p.y - last.y
        if (dx * dx + dy * dy >= minDistSq) {
            out.add(p)
            last = p
        }
    }
    val tail = points.last()
    val lastKept = out.last()
    if (lastKept != tail) {
        val dx = tail.x - lastKept.x
        val dy = tail.y - lastKept.y
        if (dx * dx + dy * dy > 1e-4f) {
            out.add(tail)
        }
    }
    return out
}

/** Лёгкое сглаживание (один проход), концы сохраняются. */
private fun smoothPoints(points: List<StrokePoint>): List<StrokePoint> {
    if (points.size < 3) return points
    val out = ArrayList<StrokePoint>(points.size)
    out.add(points.first())
    for (i in 1 until points.size - 1) {
        val p0 = points[i - 1]
        val p1 = points[i]
        val p2 = points[i + 1]
        val sx = 0.25f * p0.x + 0.5f * p1.x + 0.25f * p2.x
        val sy = 0.25f * p0.y + 0.5f * p1.y + 0.25f * p2.y
        val sp = (0.25f * p0.pressure + 0.5f * p1.pressure + 0.25f * p2.pressure).coerceIn(0.05f, 1f)
        out.add(StrokePoint(sx, sy, sp, p1.timestamp))
    }
    out.add(points.last())
    return out
}

private fun expandedStrokeBounds(bounds: StrokeBounds, pad: Float): StrokeBounds =
    StrokeBounds(
        bounds.minX - pad,
        bounds.minY - pad,
        bounds.maxX + pad,
        bounds.maxY + pad,
    )

private fun segmentCapsuleAabb(ax: Float, ay: Float, bx: Float, by: Float, radius: Float): StrokeBounds {
    val minX = min(ax, bx) - radius
    val maxX = max(ax, bx) + radius
    val minY = min(ay, by) - radius
    val maxY = max(ay, by) + radius
    return StrokeBounds(minX, minY, maxX, maxY)
}

private fun aabbIntersects(a: StrokeBounds, b: StrokeBounds): Boolean =
    !(a.maxX < b.minX || a.minX > b.maxX || a.maxY < b.minY || a.minY > b.maxY)

private fun strokeMayBeHitByEraserSegment(
    strokeBounds: StrokeBounds,
    strokeWidth: Float,
    ax: Float,
    ay: Float,
    bx: Float,
    by: Float,
    eraserRadius: Float,
): Boolean {
    val tol = eraserRadius + strokeWidth * 0.5f + 6f
    return aabbIntersects(
        expandedStrokeBounds(strokeBounds, tol),
        segmentCapsuleAabb(ax, ay, bx, by, tol),
    )
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

private fun DrawScope.drawStrokePolyline(
    reusePath: Path,
    points: List<StrokePoint>,
    colorLong: Long,
    width: Float,
) {
    when {
        points.size >= 2 -> {
            reusePath.reset()
            reusePath.moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                reusePath.lineTo(points[i].x, points[i].y)
            }
            drawPath(
                path = reusePath,
                color = Color(colorLong),
                style = Stroke(
                    width = width,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
        points.size == 1 -> {
            val p = points[0]
            drawCircle(
                color = Color(colorLong),
                radius = width / 2f,
                center = Offset(p.x, p.y),
            )
        }
    }
}
