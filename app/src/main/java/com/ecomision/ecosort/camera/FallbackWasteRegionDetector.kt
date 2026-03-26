package com.ecomision.ecosort.camera

import android.graphics.Bitmap
import android.graphics.RectF
import com.ecomision.ecosort.model.DetectionCandidate
import com.ecomision.ecosort.model.DetectionLabel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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
        val saturation: Float,
        val texture: Float
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
        val neutralRatio: Float,
        val highlightRatio: Float,
        val saturation: Float,
        val texture: Float
    )

    fun detect(
        bitmap: Bitmap,
        existingDetections: List<DetectionCandidate>
    ): FallbackDetectionResult {
        if (bitmap.width < 32 || bitmap.height < 32) {
            return FallbackDetectionResult(
                detections = emptyList(),
                sceneHint = "Analizando escena..."
            )
        }

        val cols = 30
        val rows = ((bitmap.height.toFloat() / bitmap.width.toFloat()) * cols)
            .toInt()
            .coerceIn(20, 38)
        val samples = Array(rows) { row ->
            Array(cols) { col ->
                sampleCell(bitmap, col, row, cols, rows)
            }
        }

        val allSamples = samples.flatMap { it.asList() }
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
        val meanBrightness = allSamples.map { it.brightness }.average().toFloat()
        val meanSaturation = allSamples.map { it.saturation }.average().toFloat()
        val meanTexture = allSamples.map { it.texture }.average().toFloat()

        val scoreGrid = Array(rows) { FloatArray(cols) }
        val contrastGrid = Array(rows) { FloatArray(cols) }
        var scoreSum = 0f
        var scoreSquaredSum = 0f
        var contrastSum = 0f

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val sample = samples[row][col]
                val brightnessContrast = surroundingBrightnessContrast(samples, row, col)
                val colorContrast = surroundingColorContrast(samples, row, col)
                val borderDistance = (
                    abs(sample.red - borderRed) +
                        abs(sample.green - borderGreen) +
                        abs(sample.blue - borderBlue)
                    ) / 3f
                val globalBrightnessDistance = abs(sample.brightness - meanBrightness)
                val score = (
                    borderDistance * 0.28f +
                        brightnessContrast * 0.22f +
                        colorContrast * 0.22f +
                        globalBrightnessDistance * 0.10f +
                        sample.saturation * 0.08f +
                        sample.texture * 0.10f
                    ).coerceIn(0f, 1f)
                scoreGrid[row][col] = score
                contrastGrid[row][col] = (brightnessContrast * 0.45f + colorContrast * 0.55f).coerceIn(0f, 1f)
                scoreSum += score
                scoreSquaredSum += score * score
                contrastSum += contrastGrid[row][col]
            }
        }

        val cellCount = (rows * cols).coerceAtLeast(1)
        val meanScore = scoreSum / cellCount
        val meanContrast = contrastSum / cellCount
        val scoreStd = sqrt((scoreSquaredSum / cellCount - meanScore * meanScore).coerceAtLeast(0f))
        val adaptiveThreshold = max(0.15f, meanScore + scoreStd * 0.18f)

        var active = Array(rows) { row ->
            BooleanArray(cols) { col ->
                val sample = samples[row][col]
                val score = scoreGrid[row][col]
                val contrast = contrastGrid[row][col]
                val whiteEdge = sample.brightness > 0.70f && contrast > meanContrast * 1.08f
                val texturedNeutral = sample.saturation < 0.14f && sample.texture > meanTexture * 1.08f
                score > adaptiveThreshold ||
                    (score > adaptiveThreshold - 0.03f && contrast > meanContrast * 1.05f) ||
                    (score > 0.13f && sample.saturation > meanSaturation * 1.16f) ||
                    whiteEdge ||
                    texturedNeutral
            }
        }

        repeat(2) {
            active = smoothMask(active)
        }

        val componentDetections = extractComponents(
            samples = samples,
            scoreGrid = scoreGrid,
            active = active
        ).asSequence()
            .filter { component ->
                val areaRatio = component.area / cellCount.toFloat()
                val widthCells = component.maxCol - component.minCol + 1
                val heightCells = component.maxRow - component.minRow + 1
                val aspectRatio = max(widthCells, heightCells).toFloat() / min(widthCells, heightCells).coerceAtLeast(1)
                areaRatio in 0.008f..0.74f &&
                    aspectRatio <= 7.5f &&
                    (component.meanScore > 0.16f || component.texture > meanTexture * 1.06f)
            }
            .mapIndexed { index, component ->
                buildCandidate(bitmap, component, cols, rows, index)
            }
            .toList()

        val windowDetections = if (componentDetections.size >= 3) {
            emptyList()
        } else {
            buildWindowCandidates(
                bitmap = bitmap,
                samples = samples,
                scoreGrid = scoreGrid,
                cols = cols,
                rows = rows,
                startIndex = componentDetections.size
            )
        }

        val mergedFallback = nonMaximumSuppression(
            candidates = (componentDetections + windowDetections)
                .filter { candidate ->
                    existingDetections.none { existing ->
                        intersectionOverUnion(existing.boundingBox, candidate.boundingBox) > 0.30f
                    }
                },
            limit = 5
        )

        val sceneHint = when {
            mergedFallback.isNotEmpty() ->
                "Toca un contorno punteado o toca directamente el residuo."

            meanBrightness < 0.18f ->
                "Mejora la iluminacion para detectar residuos."

            meanContrast < 0.05f ->
                "Acercate un poco mas al residuo o separalo del fondo."

            else ->
                "Si no aparece contorno, toca directamente el residuo para clasificarlo."
        }

        return FallbackDetectionResult(
            detections = mergedFallback,
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
        val spanX = ((right - left) / 3).coerceAtLeast(1)
        val spanY = ((bottom - top) / 3).coerceAtLeast(1)
        val offsets = listOf(
            0 to 0,
            -spanX to 0,
            spanX to 0,
            0 to -spanY,
            0 to spanY,
            -spanX to -spanY,
            spanX to -spanY,
            -spanX to spanY,
            spanX to spanY
        )

        val reds = mutableListOf<Float>()
        val greens = mutableListOf<Float>()
        val blues = mutableListOf<Float>()

        offsets.forEach { (dx, dy) ->
            val x = (centerX + dx).coerceIn(0, bitmap.width - 1)
            val y = (centerY + dy).coerceIn(0, bitmap.height - 1)
            val pixel = bitmap.getPixel(x, y)
            reds += android.graphics.Color.red(pixel) / 255f
            greens += android.graphics.Color.green(pixel) / 255f
            blues += android.graphics.Color.blue(pixel) / 255f
        }

        val red = reds.average().toFloat()
        val green = greens.average().toFloat()
        val blue = blues.average().toFloat()
        val maxChannel = max(red, max(green, blue))
        val minChannel = min(red, min(green, blue))
        val brightness = 0.2126f * red + 0.7152f * green + 0.0722f * blue
        val saturation = if (maxChannel == 0f) 0f else (maxChannel - minChannel) / maxChannel
        val texture = (
            reds.sumOf { abs(it - red).toDouble() } +
                greens.sumOf { abs(it - green).toDouble() } +
                blues.sumOf { abs(it - blue).toDouble() }
            ).toFloat() / (offsets.size * 3f)

        return Sample(
            red = red,
            green = green,
            blue = blue,
            brightness = brightness,
            saturation = saturation,
            texture = texture.coerceIn(0f, 1f)
        )
    }

    private fun surroundingBrightnessContrast(
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
                total += abs(samples[row][col].brightness - samples[neighborRow][neighborCol].brightness)
                count += 1
            }
        }
        return if (count == 0) 0f else total / count
    }

    private fun surroundingColorContrast(
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
                total += colorDistance(samples[row][col], samples[neighborRow][neighborCol])
                count += 1
            }
        }
        return if (count == 0) 0f else total / count
    }

    private fun colorDistance(
        first: Sample,
        second: Sample
    ): Float {
        return (
            abs(first.red - second.red) +
                abs(first.green - second.green) +
                abs(first.blue - second.blue)
            ) / 3f
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
                var neutralCount = 0
                var highlightCount = 0
                var saturationSum = 0f
                var textureSum = 0f

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
                    textureSum += sample.texture
                    if (sample.brightness > 0.76f && abs(sample.red - sample.green) < 0.10f && abs(sample.green - sample.blue) < 0.10f) {
                        whiteCount += 1
                    }
                    if (sample.red > 0.28f && sample.red < 0.76f && sample.green > 0.18f && sample.green < 0.62f && sample.blue < 0.40f) {
                        brownCount += 1
                    }
                    if (sample.green > sample.red + 0.05f && sample.green > sample.blue + 0.05f) {
                        greenCount += 1
                    }
                    if (sample.red > 0.45f && sample.green > 0.18f && sample.green < 0.72f && sample.blue < 0.40f) {
                        redCount += 1
                    }
                    if (abs(sample.red - sample.green) < 0.09f && abs(sample.green - sample.blue) < 0.09f) {
                        neutralCount += 1
                    }
                    if (sample.brightness > 0.86f) {
                        highlightCount += 1
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
                    neutralRatio = neutralCount / area.toFloat(),
                    highlightRatio = highlightCount / area.toFloat(),
                    saturation = saturationSum / area.coerceAtLeast(1),
                    texture = textureSum / area.coerceAtLeast(1)
                )
            }
        }
        return components
    }

    private fun buildWindowCandidates(
        bitmap: Bitmap,
        samples: Array<Array<Sample>>,
        scoreGrid: Array<FloatArray>,
        cols: Int,
        rows: Int,
        startIndex: Int
    ): List<DetectionCandidate> {
        val windows = listOf(
            5 to 5,
            6 to 8,
            8 to 6,
            8 to 8,
            10 to 7
        )
        val candidates = mutableListOf<DetectionCandidate>()
        var index = startIndex

        windows.forEach { (windowRows, windowCols) ->
            var bestScore = 0f
            var bestComponent: Component? = null
            for (row in 0..(rows - windowRows).coerceAtLeast(0)) {
                for (col in 0..(cols - windowCols).coerceAtLeast(0)) {
                    val component = summarizeWindow(
                        samples = samples,
                        scoreGrid = scoreGrid,
                        minCol = col,
                        minRow = row,
                        maxCol = col + windowCols - 1,
                        maxRow = row + windowRows - 1
                    )
                    val weightedScore = (
                        component.meanScore * 0.60f +
                            component.texture * 0.18f +
                            component.saturation * 0.12f +
                            component.highlightRatio * 0.10f
                        )
                    if (weightedScore > bestScore) {
                        bestScore = weightedScore
                        bestComponent = component
                    }
                }
            }
            if (bestComponent != null && bestScore > 0.17f) {
                candidates += buildCandidate(
                    bitmap = bitmap,
                    component = bestComponent,
                    cols = cols,
                    rows = rows,
                    index = index++
                )
            }
        }

        return candidates
    }

    private fun summarizeWindow(
        samples: Array<Array<Sample>>,
        scoreGrid: Array<FloatArray>,
        minCol: Int,
        minRow: Int,
        maxCol: Int,
        maxRow: Int
    ): Component {
        var area = 0
        var scoreSum = 0f
        var whiteCount = 0
        var brownCount = 0
        var greenCount = 0
        var redCount = 0
        var neutralCount = 0
        var highlightCount = 0
        var saturationSum = 0f
        var textureSum = 0f

        for (row in minRow..maxRow) {
            for (col in minCol..maxCol) {
                area += 1
                val sample = samples[row][col]
                scoreSum += scoreGrid[row][col]
                saturationSum += sample.saturation
                textureSum += sample.texture
                if (sample.brightness > 0.76f && abs(sample.red - sample.green) < 0.10f && abs(sample.green - sample.blue) < 0.10f) {
                    whiteCount += 1
                }
                if (sample.red > 0.28f && sample.red < 0.76f && sample.green > 0.18f && sample.green < 0.62f && sample.blue < 0.40f) {
                    brownCount += 1
                }
                if (sample.green > sample.red + 0.05f && sample.green > sample.blue + 0.05f) {
                    greenCount += 1
                }
                if (sample.red > 0.45f && sample.green > 0.18f && sample.green < 0.72f && sample.blue < 0.40f) {
                    redCount += 1
                }
                if (abs(sample.red - sample.green) < 0.09f && abs(sample.green - sample.blue) < 0.09f) {
                    neutralCount += 1
                }
                if (sample.brightness > 0.86f) {
                    highlightCount += 1
                }
            }
        }

        return Component(
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
            neutralRatio = neutralCount / area.toFloat(),
            highlightRatio = highlightCount / area.toFloat(),
            saturation = saturationSum / area.coerceAtLeast(1),
            texture = textureSum / area.coerceAtLeast(1)
        )
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
        val left = max(0f, component.minCol * cellWidth - cellWidth * 0.9f)
        val top = max(0f, component.minRow * cellHeight - cellHeight * 0.9f)
        val right = min(bitmap.width.toFloat(), (component.maxCol + 1) * cellWidth + cellWidth * 0.9f)
        val bottom = min(bitmap.height.toFloat(), (component.maxRow + 1) * cellHeight + cellHeight * 0.9f)
        val widthCells = component.maxCol - component.minCol + 1
        val heightCells = component.maxRow - component.minRow + 1
        val aspectRatio = max(widthCells, heightCells).toFloat() / min(widthCells, heightCells).coerceAtLeast(1)

        val label = when {
            component.greenRatio > 0.18f || component.redRatio > 0.24f ->
                "Organico probable"

            component.brownRatio > 0.22f && component.whiteRatio > 0.18f ->
                "Papel o carton"

            component.brownRatio > 0.22f ->
                "Carton probable"

            component.whiteRatio > 0.34f && component.saturation < 0.18f ->
                "Papel probable"

            component.highlightRatio > 0.14f && component.neutralRatio > 0.20f ->
                "Lata o metal"

            aspectRatio > 1.8f && component.highlightRatio > 0.08f ->
                "Botella o envase"

            aspectRatio in 1.0f..2.1f && component.highlightRatio > 0.06f ->
                "Vaso o recipiente"

            component.texture > 0.08f && component.saturation > 0.16f ->
                "Bolsa o empaque"

            else ->
                "Residuo probable"
        }
        val confidence = (
            0.24f +
                component.meanScore * 0.42f +
                component.texture * 0.14f +
                component.highlightRatio * 0.08f +
                component.saturation * 0.08f
            ).coerceIn(0.28f, 0.82f)

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

    private fun nonMaximumSuppression(
        candidates: List<DetectionCandidate>,
        limit: Int
    ): List<DetectionCandidate> {
        val kept = mutableListOf<DetectionCandidate>()
        candidates.sortedByDescending { it.confidence }.forEach { candidate ->
            val overlapsExisting = kept.any { keptCandidate ->
                intersectionOverUnion(keptCandidate.boundingBox, candidate.boundingBox) > 0.34f
            }
            if (!overlapsExisting) {
                kept += candidate
            }
        }
        return kept.take(limit)
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
