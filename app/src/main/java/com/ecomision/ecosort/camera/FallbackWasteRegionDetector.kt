package com.ecomision.ecosort.camera

import android.graphics.Bitmap
import android.graphics.RectF
import com.ecomision.ecosort.model.DetectionCandidate
import com.ecomision.ecosort.model.DetectionLabel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class FallbackDetectionResult(
    val detections: List<DetectionCandidate>,
    val sceneHint: String
)

class FallbackWasteRegionDetector {

    private data class Sample(
        val red: Float,
        val green: Float,
        val blue: Float,
        val brightness: Float,
        val saturation: Float
    )

    private data class Component(
        val minCol: Int,
        val minRow: Int,
        val maxCol: Int,
        val maxRow: Int,
        val area: Int,
        val meanScore: Float,
        val whiteRatio: Float,
        val brownRatio: Float,
        val greenRatio: Float,
        val redRatio: Float,
        val saturation: Float
    )

    fun detect(
        bitmap: Bitmap,
        existingDetections: List<DetectionCandidate>
    ): FallbackDetectionResult {
        if (bitmap.width < 32 || bitmap.height < 32) {
            return FallbackDetectionResult(
                detections = emptyList(),
                sceneHint = "Analizando..."
            )
        }

        val cols = 24
        val rows = ((bitmap.height.toFloat() / bitmap.width.toFloat()) * cols)
            .toInt()
            .coerceIn(18, 34)
        val samples = Array(rows) { row ->
            Array(cols) { col ->
                sampleCell(bitmap, col, row, cols, rows)
            }
        }

        val borderSamples = buildList {
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    if (row == 0 || col == 0 || row == rows - 1 || col == cols - 1) {
                        add(samples[row][col])
                    }
                }
            }
        }
        val borderRed = borderSamples.map { it.red }.average().toFloat()
        val borderGreen = borderSamples.map { it.green }.average().toFloat()
        val borderBlue = borderSamples.map { it.blue }.average().toFloat()
        val meanBrightness = samples.flatMap { it.asList() }.map { it.brightness }.average().toFloat()

        val scoreGrid = Array(rows) { FloatArray(cols) }
        var contrastAccumulator = 0f
        var saturationAccumulator = 0f

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val sample = samples[row][col]
                val neighborBrightness = surroundingBrightness(samples, row, col)
                val localContrast = abs(sample.brightness - neighborBrightness)
                val borderDistance = (
                    abs(sample.red - borderRed) +
                        abs(sample.green - borderGreen) +
                        abs(sample.blue - borderBlue)
                    ) / 3f
                val score = (
                    borderDistance * 0.50f +
                        localContrast * 0.32f +
                        sample.saturation * 0.18f
                    ).coerceIn(0f, 1f)
                scoreGrid[row][col] = score
                contrastAccumulator += localContrast
                saturationAccumulator += sample.saturation
            }
        }

        val meanContrast = contrastAccumulator / (rows * cols).coerceAtLeast(1)
        val meanSaturation = saturationAccumulator / (rows * cols).coerceAtLeast(1)

        var active = Array(rows) { row ->
            BooleanArray(cols) { col ->
                val sample = samples[row][col]
                val score = scoreGrid[row][col]
                (score > 0.19f && sample.brightness in 0.06f..0.98f) ||
                    (score > 0.16f && sample.saturation > 0.18f)
            }
        }

        repeat(2) {
            active = smoothMask(active)
        }

        val components = extractComponents(
            samples = samples,
            scoreGrid = scoreGrid,
            active = active
        )
        val fallbackDetections = components
            .filter { component ->
                val areaRatio = component.area / (rows * cols).toFloat()
                val widthCells = component.maxCol - component.minCol + 1
                val heightCells = component.maxRow - component.minRow + 1
                val aspectRatio = max(widthCells, heightCells).toFloat() / min(widthCells, heightCells).coerceAtLeast(1)
                areaRatio in 0.02f..0.48f &&
                    aspectRatio <= 5.5f &&
                    !(touchesFrameBorder(component, cols, rows) && areaRatio > 0.20f)
            }
            .mapIndexed { index, component ->
                buildCandidate(bitmap, component, cols, rows, index)
            }
            .filter { candidate ->
                existingDetections.none { existing ->
                    intersectionOverUnion(existing.boundingBox, candidate.boundingBox) > 0.34f
                }
            }
            .sortedByDescending { it.confidence }
            .take(4)

        val sceneHint = when {
            existingDetections.isNotEmpty() || fallbackDetections.isNotEmpty() ->
                "Toca uno de los contornos punteados para clasificarlo."

            meanBrightness < 0.18f ->
                "Mejora la iluminacion para detectar residuos."

            meanContrast < 0.055f ->
                "Acercate un poco mas al objeto para detectarlo."

            meanSaturation < 0.08f && meanContrast < 0.09f ->
                "No hay elementos claros en la escena. Apunta la camara hacia un residuo."

            else ->
                "Mejora el encuadre y separa el objeto del fondo."
        }

        return FallbackDetectionResult(
            detections = fallbackDetections,
            sceneHint = sceneHint
        )
    }

    private fun sampleCell(
        bitmap: Bitmap,
        col: Int,
        row: Int,
        cols: Int,
        rows: Int
    ): Sample {
        val left = col * bitmap.width / cols
        val right = ((col + 1) * bitmap.width / cols).coerceAtMost(bitmap.width)
        val top = row * bitmap.height / rows
        val bottom = ((row + 1) * bitmap.height / rows).coerceAtMost(bitmap.height)
        val centerX = ((left + right) / 2).coerceIn(0, bitmap.width - 1)
        val centerY = ((top + bottom) / 2).coerceIn(0, bitmap.height - 1)
        val offsets = listOf(
            0 to 0,
            -((right - left) / 4) to 0,
            ((right - left) / 4) to 0,
            0 to -((bottom - top) / 4),
            0 to ((bottom - top) / 4)
        )

        var red = 0f
        var green = 0f
        var blue = 0f

        offsets.forEach { (dx, dy) ->
            val x = (centerX + dx).coerceIn(0, bitmap.width - 1)
            val y = (centerY + dy).coerceIn(0, bitmap.height - 1)
            val pixel = bitmap.getPixel(x, y)
            red += android.graphics.Color.red(pixel) / 255f
            green += android.graphics.Color.green(pixel) / 255f
            blue += android.graphics.Color.blue(pixel) / 255f
        }

        red /= offsets.size
        green /= offsets.size
        blue /= offsets.size
        val maxChannel = max(red, max(green, blue))
        val minChannel = min(red, min(green, blue))
        val brightness = 0.2126f * red + 0.7152f * green + 0.0722f * blue
        val saturation = if (maxChannel == 0f) 0f else (maxChannel - minChannel) / maxChannel

        return Sample(
            red = red,
            green = green,
            blue = blue,
            brightness = brightness,
            saturation = saturation
        )
    }

    private fun surroundingBrightness(
        samples: Array<Array<Sample>>,
        row: Int,
        col: Int
    ): Float {
        var total = 0f
        var count = 0
        for (rowOffset in -1..1) {
            for (colOffset in -1..1) {
                if (rowOffset == 0 && colOffset == 0) continue
                val neighborRow = row + rowOffset
                val neighborCol = col + colOffset
                if (neighborRow !in samples.indices || neighborCol !in samples[neighborRow].indices) continue
                total += samples[neighborRow][neighborCol].brightness
                count += 1
            }
        }
        return if (count == 0) samples[row][col].brightness else total / count
    }

    private fun smoothMask(active: Array<BooleanArray>): Array<BooleanArray> {
        val rows = active.size
        val cols = active.firstOrNull()?.size ?: 0
        return Array(rows) { row ->
            BooleanArray(cols) { col ->
                val neighbors = activeNeighborCount(active, row, col)
                when {
                    active[row][col] -> neighbors >= 2
                    else -> neighbors >= 5
                }
            }
        }
    }

    private fun activeNeighborCount(
        active: Array<BooleanArray>,
        row: Int,
        col: Int
    ): Int {
        var count = 0
        for (rowOffset in -1..1) {
            for (colOffset in -1..1) {
                if (rowOffset == 0 && colOffset == 0) continue
                val neighborRow = row + rowOffset
                val neighborCol = col + colOffset
                if (neighborRow !in active.indices || neighborCol !in active[neighborRow].indices) continue
                if (active[neighborRow][neighborCol]) count += 1
            }
        }
        return count
    }

    private fun extractComponents(
        samples: Array<Array<Sample>>,
        scoreGrid: Array<FloatArray>,
        active: Array<BooleanArray>
    ): List<Component> {
        val rows = active.size
        val cols = active.firstOrNull()?.size ?: 0
        val visited = Array(rows) { BooleanArray(cols) }
        val components = mutableListOf<Component>()
        val queue = ArrayDeque<Pair<Int, Int>>()

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                if (!active[row][col] || visited[row][col]) continue
                visited[row][col] = true
                queue.add(row to col)

                var minCol = col
                var maxCol = col
                var minRow = row
                var maxRow = row
                var area = 0
                var scoreSum = 0f
                var whiteCount = 0
                var brownCount = 0
                var greenCount = 0
                var redCount = 0
                var saturationSum = 0f

                while (queue.isNotEmpty()) {
                    val (currentRow, currentCol) = queue.removeFirst()
                    area += 1
                    minCol = min(minCol, currentCol)
                    maxCol = max(maxCol, currentCol)
                    minRow = min(minRow, currentRow)
                    maxRow = max(maxRow, currentRow)
                    val sample = samples[currentRow][currentCol]
                    scoreSum += scoreGrid[currentRow][currentCol]
                    saturationSum += sample.saturation
                    if (sample.brightness > 0.78f && abs(sample.red - sample.green) < 0.12f && abs(sample.green - sample.blue) < 0.12f) {
                        whiteCount += 1
                    }
                    if (sample.red > 0.28f && sample.red < 0.74f && sample.green > 0.18f && sample.green < 0.56f && sample.blue < 0.34f) {
                        brownCount += 1
                    }
                    if (sample.green > sample.red + 0.06f && sample.green > sample.blue + 0.06f) {
                        greenCount += 1
                    }
                    if (sample.red > 0.45f && sample.green > 0.18f && sample.green < 0.68f && sample.blue < 0.38f) {
                        redCount += 1
                    }

                    for (rowOffset in -1..1) {
                        for (colOffset in -1..1) {
                            val neighborRow = currentRow + rowOffset
                            val neighborCol = currentCol + colOffset
                            if (neighborRow !in 0 until rows || neighborCol !in 0 until cols) continue
                            if (!active[neighborRow][neighborCol] || visited[neighborRow][neighborCol]) continue
                            visited[neighborRow][neighborCol] = true
                            queue.add(neighborRow to neighborCol)
                        }
                    }
                }

                components += Component(
                    minCol = minCol,
                    minRow = minRow,
                    maxCol = maxCol,
                    maxRow = maxRow,
                    area = area,
                    meanScore = scoreSum / area.coerceAtLeast(1),
                    whiteRatio = whiteCount / area.toFloat(),
                    brownRatio = brownCount / area.toFloat(),
                    greenRatio = greenCount / area.toFloat(),
                    redRatio = redCount / area.toFloat(),
                    saturation = saturationSum / area.coerceAtLeast(1)
                )
            }
        }
        return components
    }

    private fun touchesFrameBorder(
        component: Component,
        cols: Int,
        rows: Int
    ): Boolean {
        return component.minCol == 0 ||
            component.minRow == 0 ||
            component.maxCol == cols - 1 ||
            component.maxRow == rows - 1
    }

    private fun buildCandidate(
        bitmap: Bitmap,
        component: Component,
        cols: Int,
        rows: Int,
        index: Int
    ): DetectionCandidate {
        val cellWidth = bitmap.width / cols.toFloat()
        val cellHeight = bitmap.height / rows.toFloat()
        val left = max(0f, component.minCol * cellWidth - cellWidth * 0.8f)
        val top = max(0f, component.minRow * cellHeight - cellHeight * 0.8f)
        val right = min(bitmap.width.toFloat(), (component.maxCol + 1) * cellWidth + cellWidth * 0.8f)
        val bottom = min(bitmap.height.toFloat(), (component.maxRow + 1) * cellHeight + cellHeight * 0.8f)
        val label = when {
            component.whiteRatio > 0.42f && component.brownRatio < 0.18f ->
                "Papel probable"

            component.brownRatio > 0.18f ->
                "Carton probable"

            component.greenRatio > 0.18f || component.redRatio > 0.16f ->
                "Organico probable"

            component.saturation > 0.18f ->
                "Envase probable"

            else ->
                "Objeto probable"
        }
        val confidence = (0.28f + component.meanScore * 0.62f).coerceIn(0.32f, 0.78f)

        return DetectionCandidate(
            id = -1L - index,
            trackingId = null,
            boundingBox = RectF(left, top, right, bottom),
            labels = listOf(
                DetectionLabel(
                    text = label,
                    confidence = confidence
                )
            ),
            confidence = confidence
        )
    }

    private fun intersectionOverUnion(
        first: RectF,
        second: RectF
    ): Float {
        val left = max(first.left, second.left)
        val top = max(first.top, second.top)
        val right = min(first.right, second.right)
        val bottom = min(first.bottom, second.bottom)
        val intersectionWidth = max(0f, right - left)
        val intersectionHeight = max(0f, bottom - top)
        val intersectionArea = intersectionWidth * intersectionHeight
        if (intersectionArea <= 0f) return 0f

        val firstArea = first.width().coerceAtLeast(0f) * first.height().coerceAtLeast(0f)
        val secondArea = second.width().coerceAtLeast(0f) * second.height().coerceAtLeast(0f)
        val unionArea = (firstArea + secondArea - intersectionArea).coerceAtLeast(1f)
        return (intersectionArea / unionArea).coerceIn(0f, 1f)
    }
}
