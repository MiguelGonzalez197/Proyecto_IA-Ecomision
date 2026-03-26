package com.ecomision.ecosort.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.ecomision.ecosort.model.DetectionCandidate
import com.ecomision.ecosort.model.EvidenceSlot
import com.ecomision.ecosort.model.WasteCategory
import com.ecomision.ecosort.model.WasteFamily
import com.ecomision.ecosort.model.WasteObservation
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.abs
import kotlin.math.max

class WasteHeuristicClassifier(
    context: Context
) {
    private val appContext = context.applicationContext
    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    }

    suspend fun analyze(
        bitmap: Bitmap,
        detection: DetectionCandidate,
        slot: EvidenceSlot,
        catalog: List<WasteCategory>
    ): WasteObservation {
        val labels = labelBitmap(bitmap)
        val hintTexts = (labels + detection.labels.map { it.text.lowercase() }).distinct()
        val features = ImageFeatures.fromBitmap(bitmap)
        val categoryScores = catalog.associateWith { scoreCategory(it.id, hintTexts, features, bitmap) }
        val best = categoryScores.maxByOrNull { it.value }
        val category = best?.key
        val confidence = (best?.value ?: 0.15f).coerceIn(0f, 1f)
        val tokens = buildExplanationTokens(category?.displayName, hintTexts, features, slot)

        return WasteObservation(
            slot = slot,
            candidateId = detection.id,
            categoryId = category?.id,
            family = category?.family ?: WasteFamily.UNKNOWN,
            classifierConfidence = confidence,
            labels = hintTexts,
            cleanScore = features.cleanScore.coerceIn(0f, 1f),
            liquidScore = liquidScoreFor(slot, hintTexts, features).coerceIn(0f, 1f),
            residueScore = residueScoreFor(hintTexts, features).coerceIn(0f, 1f),
            greaseScore = greaseScoreFor(hintTexts, features).coerceIn(0f, 1f),
            moistureScore = moistureScoreFor(hintTexts, features).coerceIn(0f, 1f),
            organicScore = organicScoreFor(hintTexts, features, category?.family).coerceIn(0f, 1f),
            blurScore = features.blurScore.coerceIn(0f, 1f),
            occlusionScore = occlusionScoreFor(detection),
            explanationTokens = tokens
        )
    }

    private suspend fun labelBitmap(bitmap: Bitmap): List<String> {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        return runCatching {
            labeler.process(inputImage).await()
                .filter { it.confidence >= 0.45f }
                .map { it.text.lowercase() }
        }.getOrDefault(emptyList())
    }

    private fun scoreCategory(
        categoryId: String,
        hints: List<String>,
        features: ImageFeatures,
        bitmap: Bitmap
    ): Float {
        var score = 0.12f
        val aspectRatio = bitmap.height.toFloat() / max(bitmap.width.toFloat(), 1f)

        fun hasAny(vararg keywords: String): Boolean {
            return hints.any { label -> keywords.any { keyword -> label.contains(keyword) } }
        }

        score += when (categoryId) {
            "plastic_bottle" -> keywordScore(hints, "bottle", "water", "drink", "beverage") +
                if (aspectRatio > 1.35f) 0.18f else 0f

            "plastic_container" -> keywordScore(hints, "container", "box", "cup", "food", "packaged goods")
            "plastic_bag" -> keywordScore(hints, "bag", "plastic bag", "sack", "package")
            "paper_sheet" -> keywordScore(hints, "paper", "document", "book", "text", "newspaper", "receipt") +
                if (features.whiteRatio > 0.35f) 0.12f else 0f

            "cardboard_box" -> keywordScore(hints, "box", "cardboard", "carton", "package")
            "pizza_box" -> keywordScore(hints, "pizza", "box", "food", "cardboard") +
                if (features.brownRatio > 0.18f) 0.12f else 0f

            "disposable_cup" -> keywordScore(hints, "cup", "drinkware", "glass") +
                if (aspectRatio in 0.8f..1.7f) 0.12f else 0f

            "coffee_cup" -> keywordScore(hints, "coffee", "cup", "mug", "drinkware") +
                if (features.darkRatio > 0.30f) 0.12f else 0f

            "can" -> keywordScore(hints, "can", "tin", "beverage", "drink") +
                if (aspectRatio in 0.8f..1.5f) 0.12f else 0f

            "glass_jar" -> keywordScore(hints, "jar", "glass", "bottle", "container") +
                if (features.whiteRatio > 0.20f) 0.08f else 0f

            "tetra_pak" -> keywordScore(hints, "carton", "juice", "milk", "drink", "box")
            "metalized_wrapper" -> keywordScore(hints, "package", "snack", "wrapper", "candy", "packet") +
                if (features.highlightRatio > 0.18f) 0.12f else 0f

            "foam_tray" -> keywordScore(hints, "tray", "plate", "platter") +
                if (features.whiteRatio > 0.55f) 0.16f else 0f

            "delivery_container" -> keywordScore(hints, "container", "food", "lunch", "box", "plate")
            "napkin" -> keywordScore(hints, "napkin", "tissue", "paper towel", "cloth") +
                if (features.whiteRatio > 0.50f) 0.10f else 0f

            "organic_food" -> keywordScore(hints, "food", "fruit", "banana", "apple", "orange", "vegetable", "bread", "plant") +
                if (features.greenRatio > 0.18f || features.redOrangeRatio > 0.20f) 0.14f else 0f

            "aluminum_foil" -> keywordScore(hints, "foil", "silver", "metal", "aluminum") +
                if (features.highlightRatio > 0.20f) 0.12f else 0f

            "cutlery" -> keywordScore(hints, "fork", "spoon", "knife", "cutlery")
            "lid" -> keywordScore(hints, "lid", "cap", "cover")
            else -> 0f
        }

        if (categoryId == "organic_food" && hasAny("bottle", "can", "glass", "jar")) {
            score -= 0.18f
        }
        if (categoryId == "napkin" && hasAny("bottle", "cup")) {
            score -= 0.12f
        }
        return score.coerceIn(0f, 1f)
    }

    private fun keywordScore(hints: List<String>, vararg keywords: String): Float {
        var matches = 0
        hints.forEach { label ->
            if (keywords.any { label.contains(it) }) matches += 1
        }
        return (matches * 0.18f).coerceAtMost(0.6f)
    }

    private fun liquidScoreFor(slot: EvidenceSlot, hints: List<String>, features: ImageFeatures): Float {
        var score = features.highlightRatio * 0.25f + features.darkRatio * 0.22f
        if (slot == EvidenceSlot.INNER_VIEW || slot == EvidenceSlot.OPENING_NECK) score += 0.18f
        if (hints.any { it.contains("drink") || it.contains("water") || it.contains("coffee") || it.contains("juice") }) {
            score += 0.22f
        }
        return score
    }

    private fun residueScoreFor(hints: List<String>, features: ImageFeatures): Float {
        var score = features.brownRatio * 0.55f + features.redOrangeRatio * 0.45f
        if (hints.any { it.contains("food") || it.contains("pizza") || it.contains("sauce") || it.contains("meal") }) {
            score += 0.22f
        }
        return score
    }

    private fun greaseScoreFor(hints: List<String>, features: ImageFeatures): Float {
        var score = features.brownRatio * 0.6f + features.highlightRatio * 0.20f
        if (hints.any { it.contains("pizza") || it.contains("food") || it.contains("fried") }) {
            score += 0.18f
        }
        return score
    }

    private fun moistureScoreFor(hints: List<String>, features: ImageFeatures): Float {
        var score = features.darkRatio * 0.25f + features.highlightRatio * 0.25f
        if (hints.any { it.contains("drink") || it.contains("juice") || it.contains("water") }) {
            score += 0.16f
        }
        return score
    }

    private fun organicScoreFor(hints: List<String>, features: ImageFeatures, family: WasteFamily?): Float {
        var score = 0f
        if (family == WasteFamily.ORGANIC) score += 0.25f
        if (hints.any { it.contains("food") || it.contains("fruit") || it.contains("banana") || it.contains("vegetable") || it.contains("bread") || it.contains("plant") }) {
            score += 0.45f
        }
        score += features.greenRatio * 0.15f + features.redOrangeRatio * 0.1f
        return score
    }

    private fun occlusionScoreFor(detection: DetectionCandidate): Float {
        return (0.45f - detection.confidence).coerceAtLeast(0f) * 1.8f
    }

    private fun buildExplanationTokens(
        categoryName: String?,
        hints: List<String>,
        features: ImageFeatures,
        slot: EvidenceSlot
    ): List<String> {
        val tokens = mutableListOf<String>()
        if (categoryName != null) tokens += "Categoria estimada: $categoryName."
        if (slot != EvidenceSlot.OUTER_FULL) tokens += "Se analizo una vista guiada: ${slot.name.lowercase()}."
        if (features.cleanScore > 0.65f) tokens += "La superficie se ve clara y relativamente limpia."
        if (features.brownRatio > 0.22f || features.redOrangeRatio > 0.22f) tokens += "Se observan manchas o restos adheridos."
        if (features.highlightRatio > 0.20f) tokens += "Hay reflejos o zonas humedas que merecen validacion."
        if (hints.isNotEmpty()) tokens += "Etiquetas visuales: ${hints.take(4).joinToString()}."
        return tokens
    }
}

private data class ImageFeatures(
    val meanBrightness: Float,
    val whiteRatio: Float,
    val darkRatio: Float,
    val brownRatio: Float,
    val greenRatio: Float,
    val redOrangeRatio: Float,
    val highlightRatio: Float,
    val blurScore: Float,
    val cleanScore: Float
) {
    companion object {
        fun fromBitmap(bitmap: Bitmap): ImageFeatures {
            val width = bitmap.width
            val height = bitmap.height
            val stepX = max(1, width / 40)
            val stepY = max(1, height / 40)

            var samples = 0
            var brightnessSum = 0f
            var whiteCount = 0
            var darkCount = 0
            var brownCount = 0
            var greenCount = 0
            var redOrangeCount = 0
            var highlightCount = 0
            var edgeSum = 0f

            for (y in 0 until height step stepY) {
                for (x in 0 until width step stepX) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = Color.red(pixel) / 255f
                    val g = Color.green(pixel) / 255f
                    val b = Color.blue(pixel) / 255f
                    val brightness = 0.2126f * r + 0.7152f * g + 0.0722f * b
                    brightnessSum += brightness
                    samples += 1

                    if (brightness > 0.78f && abs(r - g) < 0.12f && abs(g - b) < 0.12f) whiteCount += 1
                    if (brightness < 0.22f) darkCount += 1
                    if (r > 0.30f && r < 0.72f && g > 0.16f && g < 0.55f && b < 0.30f) brownCount += 1
                    if (g > r + 0.05f && g > b + 0.05f) greenCount += 1
                    if (r > 0.45f && g > 0.18f && g < 0.65f && b < 0.35f) redOrangeCount += 1
                    if (brightness > 0.88f) highlightCount += 1

                    if (x + stepX < width) {
                        val neighbor = bitmap.getPixel(x + stepX, y)
                        edgeSum += colorDelta(pixel, neighbor)
                    }
                    if (y + stepY < height) {
                        val neighbor = bitmap.getPixel(x, y + stepY)
                        edgeSum += colorDelta(pixel, neighbor)
                    }
                }
            }

            val sampleCount = samples.coerceAtLeast(1).toFloat()
            val meanBrightness = brightnessSum / sampleCount
            val whiteRatio = whiteCount / sampleCount
            val darkRatio = darkCount / sampleCount
            val brownRatio = brownCount / sampleCount
            val greenRatio = greenCount / sampleCount
            val redOrangeRatio = redOrangeCount / sampleCount
            val highlightRatio = highlightCount / sampleCount
            val sharpness = (edgeSum / sampleCount / 255f).coerceIn(0f, 1f)
            val blurScore = (1f - sharpness).coerceIn(0f, 1f)
            val cleanScore = (whiteRatio * 0.55f + meanBrightness * 0.35f - brownRatio * 0.35f - darkRatio * 0.2f)
                .coerceIn(0f, 1f)

            return ImageFeatures(
                meanBrightness = meanBrightness,
                whiteRatio = whiteRatio,
                darkRatio = darkRatio,
                brownRatio = brownRatio,
                greenRatio = greenRatio,
                redOrangeRatio = redOrangeRatio,
                highlightRatio = highlightRatio,
                blurScore = blurScore,
                cleanScore = cleanScore
            )
        }

        private fun colorDelta(first: Int, second: Int): Float {
            return abs(Color.red(first) - Color.red(second)).toFloat() +
                abs(Color.green(first) - Color.green(second)).toFloat() +
                abs(Color.blue(first) - Color.blue(second)).toFloat()
        }
    }
}
