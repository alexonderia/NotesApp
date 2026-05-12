package com.example.notesapp.core.model

data class StrokeBounds(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
) {
    val centerY: Float get() = (minY + maxY) / 2f
    val centerX: Float get() = (minX + maxX) / 2f
    val height: Float get() = (maxY - minY).coerceAtLeast(0f)
    val width: Float get() = (maxX - minX).coerceAtLeast(0f)

    fun merge(other: StrokeBounds): StrokeBounds =
        StrokeBounds(
            minOf(minX, other.minX),
            minOf(minY, other.minY),
            maxOf(maxX, other.maxX),
            maxOf(maxY, other.maxY),
        )

    companion object {
        fun fromStroke(stroke: InkStroke): StrokeBounds {
            val pts = stroke.points
            if (pts.isEmpty()) {
                return StrokeBounds(0f, 0f, 0f, 0f)
            }
            var minX = pts[0].x
            var minY = pts[0].y
            var maxX = pts[0].x
            var maxY = pts[0].y
            for (i in 1 until pts.size) {
                val p = pts[i]
                minX = minOf(minX, p.x)
                minY = minOf(minY, p.y)
                maxX = maxOf(maxX, p.x)
                maxY = maxOf(maxY, p.y)
            }
            return StrokeBounds(minX, minY, maxX, maxY)
        }
    }
}
