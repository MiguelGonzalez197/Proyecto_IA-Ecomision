package com.ecomision.ecosort.analysis

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
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max

class WasteHeuristicClassifier {
    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    }

    suspend fun analyze(
        bitmap: Bitmap,
        detection: DetectionCandidate,
        slot: EvidenceSlot,
        catalog: List<WasteCategory>
    ): WasteObservation {
        val labelHints = labelBitmap(bitmap)
        val rawHints = (labelHints + detection.labels.map { it.text }).distinct()
        val normalizedHints = expandHints(rawHints)
        val features = ImageFeatures.fromBitmap(bitmap)
        val categoryScores = catalog.associateWith { category ->
            scoreCategory(
                category = category,
                hints = normalizedHints,
                features = features,
                bitmap = bitmap
            )
        }
        val sortedScores = categoryScores.entries.sortedByDescending { it.value }
        val best = sortedScores.firstOrNull()
        val secondScore = sortedScores.getOrNull(1)?.value ?: 0f
        val commonWaste = catalog.firstOrNull { it.id == COMMON_WASTE_ID }
        val resolvedCategory = when {
            best == null -> commonWaste
            best.value < 0.25f -> commonWaste ?: best.key
            else -> best.key
        }
        val bestScore = when {
            resolvedCategory == null -> 0.18f
            resolvedCategory.id == COMMON_WASTE_ID && best != null && best.value < 0.25f -> 0.22f
            else -> best?.value ?: 0.18f
        }
        val margin = (bestScore - secondScore).coerceAtLeast(0f)
        val confidence = (
            bestScore * 0.62f +
                detection.confidence.coerceIn(0f, 1f) * 0.18f +
                margin * 0.12f +
                (1f - features.blurScore.coerceIn(0f, 1f)) * 0.08f
            ).coerceIn(
                minimumValue = if (resolvedCategory?.id == COMMON_WASTE_ID) 0.24f else 0.18f,
                maximumValue = if (resolvedCategory?.id == COMMON_WASTE_ID) 0.54f else 0.96f
            )
        val tokens = buildExplanationTokens(
            categoryName = resolvedCategory?.displayName,
            rawHints = rawHints,
            features = features,
            slot = slot
        )

        return WasteObservation(
            slot = slot,
            candidateId = detection.id,
            categoryId = resolvedCategory?.id,
            family = resolvedCategory?.family ?: WasteFamily.UNKNOWN,
            classifierConfidence = confidence,
            labels = rawHints,
            cleanScore = features.cleanScore.coerceIn(0f, 1f),
            liquidScore = liquidScoreFor(slot, normalizedHints, features).coerceIn(0f, 1f),
            residueScore = residueScoreFor(normalizedHints, features).coerceIn(0f, 1f),
            greaseScore = greaseScoreFor(normalizedHints, features).coerceIn(0f, 1f),
            moistureScore = moistureScoreFor(normalizedHints, features).coerceIn(0f, 1f),
            organicScore = organicScoreFor(normalizedHints, features, resolvedCategory?.family).coerceIn(0f, 1f),
            blurScore = features.blurScore.coerceIn(0f, 1f),
            occlusionScore = occlusionScoreFor(detection),
            explanationTokens = tokens
        )
    }

    private suspend fun labelBitmap(bitmap: Bitmap): List<String> {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        return runCatching {
            labeler.process(inputImage).await()
                .filter { it.confidence >= 0.20f }
                .map { it.text }
        }.getOrDefault(emptyList())
    }

    private fun expandHints(rawHints: List<String>): Set<String> {
        val expanded = linkedSetOf<String>()
        rawHints.forEach { raw ->
            val normalized = normalizeText(raw)
            if (normalized.isBlank()) return@forEach
            expanded += normalized

            when {
                normalized.containsAny("botella", "bottle", "envase alto", "frasco") -> {
                    expanded += "bottle"
                    expanded += "container"
                }

                normalized.containsAny("vidrio", "glass", "jar", "frasco") -> {
                    expanded += "glass"
                    expanded += "jar"
                }

                normalized.containsAny("vaso", "cup", "drinkware", "mug", "coffee") -> {
                    expanded += "cup"
                    expanded += "container"
                }

                normalized.containsAny("lata", "can", "tin", "aluminum", "metal") -> {
                    expanded += "can"
                    expanded += "metal"
                }

                normalized.containsAny("bolsa", "bag", "sack") -> {
                    expanded += "bag"
                    expanded += "flexible package"
                }

                normalized.containsAny("empaque", "wrapper", "package", "snack") -> {
                    expanded += "package"
                    expanded += "wrapper"
                }

                normalized.containsAny("papel", "paper", "sheet", "document", "receipt") -> {
                    expanded += "paper"
                }

                normalized.containsAny("carton", "cardboard", "box", "caja", "paperboard") -> {
                    expanded += "cardboard"
                    expanded += "box"
                }

                normalized.containsAny("servilleta", "napkin", "tissue", "paper towel") -> {
                    expanded += "napkin"
                    expanded += "paper"
                }

                normalized.containsAny("tetra", "milk", "juice", "carton") -> {
                    expanded += "tetra pak"
                    expanded += "carton"
                }

                normalized.containsAny("icopor", "foam", "tray", "plate") -> {
                    expanded += "foam"
                    expanded += "tray"
                }

                normalized.containsAny("cubierto", "fork", "spoon", "knife", "cutlery") -> {
                    expanded += "cutlery"
                }

                normalized.containsAny("tapa", "lid", "cap", "cover") -> {
                    expanded += "lid"
                }

                normalized.containsAny("banana", "fruit", "fruta", "vegetable", "verdura", "food", "organico", "organic", "peel", "scrap", "resto") -> {
                    expanded += "organic"
                    expanded += "food"
                }

                normalized.containsAny("cafe", "coffee filter", "filtro") -> {
                    expanded += "coffee"
                    expanded += "filter"
                }
            }
        }
        return expanded
    }

    private fun scoreCategory(
        category: WasteCategory,
        hints: Set<String>,
        features: ImageFeatures,
        bitmap: Bitmap
    ): Float {
        val aspectRatio = bitmap.height.toFloat() / max(bitmap.width.toFloat(), 1f)
        val tallObject = aspectRatio > 1.20f
        val flatObject = aspectRatio < 0.68f
        var score = when (category.family) {
            WasteFamily.PAPER,
            WasteFamily.CARDBOARD,
            WasteFamily.SANITARY_PAPER -> 0.10f

            WasteFamily.ORGANIC -> 0.08f
            WasteFamily.UNKNOWN -> 0.12f
            else -> 0.11f
        }

        score += when (category.id) {
            "plastic_bottle" ->
                keywordScore(hints, "bottle", "container", "drink", "beverage", "plastic") +
                    scoreIf(tallObject, 0.18f) +
                    scoreIf(features.highlightRatio > 0.12f || features.neutralRatio > 0.25f, 0.12f)

            "glass_bottle" ->
                keywordScore(hints, "glass", "bottle", "jar", "transparent") +
                    scoreIf(tallObject, 0.16f) +
                    scoreIf(features.highlightRatio > 0.16f && features.neutralRatio > 0.20f, 0.14f)

            "glass_jar" ->
                keywordScore(hints, "glass", "jar", "container", "frasco") +
                    scoreIf(aspectRatio in 0.75f..1.65f, 0.13f) +
                    scoreIf(features.highlightRatio > 0.14f, 0.10f)

            "plastic_container" ->
                keywordScore(hints, "container", "package", "food", "box", "plastic") +
                    scoreIf(aspectRatio in 0.55f..1.90f, 0.12f) +
                    scoreIf(features.saturationMean > 0.14f || features.highlightRatio > 0.10f, 0.08f)

            "yogurt_cup" ->
                keywordScore(hints, "cup", "container", "yogurt", "dairy") +
                    scoreIf(aspectRatio in 0.70f..1.70f, 0.12f) +
                    scoreIf(features.highlightRatio > 0.08f || features.whiteRatio > 0.22f, 0.08f)

            "ice_cream_tub" ->
                keywordScore(hints, "container", "tub", "ice cream", "food") +
                    scoreIf(aspectRatio in 0.45f..1.40f, 0.12f) +
                    scoreIf(features.whiteRatio > 0.18f || features.saturationMean > 0.15f, 0.06f)

            "plastic_cup" ->
                keywordScore(hints, "cup", "drink", "container", "vaso") +
                    scoreIf(aspectRatio in 0.70f..1.90f, 0.16f) +
                    scoreIf(features.highlightRatio > 0.10f || features.neutralRatio > 0.18f, 0.08f)

            "coffee_cup" ->
                keywordScore(hints, "cup", "coffee", "mug", "drink") +
                    scoreIf(aspectRatio in 0.80f..1.90f, 0.14f) +
                    scoreIf(features.darkRatio > 0.26f || features.brownRatio > 0.18f, 0.10f)

            "can" ->
                keywordScore(hints, "can", "metal", "aluminum", "tin", "lata") +
                    scoreIf(aspectRatio in 0.70f..1.70f, 0.14f) +
                    scoreIf(features.highlightRatio > 0.14f && features.neutralRatio > 0.24f, 0.12f)

            "cardboard" ->
                keywordScore(hints, "cardboard", "carton", "paperboard") +
                    scoreIf(features.brownRatio > 0.18f, 0.18f) +
                    scoreIf(features.textureScore > 0.16f, 0.06f)

            "cardboard_box" ->
                keywordScore(hints, "cardboard", "box", "carton", "caja") +
                    scoreIf(features.brownRatio > 0.16f, 0.16f) +
                    scoreIf(aspectRatio in 0.45f..1.90f, 0.10f)

            "pizza_box" ->
                keywordScore(hints, "pizza", "box", "cardboard", "food") +
                    scoreIf(features.brownRatio > 0.16f || features.redOrangeRatio > 0.10f, 0.14f) +
                    scoreIf(flatObject, 0.08f)

            "paper_sheet" ->
                keywordScore(hints, "paper", "sheet", "document", "receipt", "newspaper") +
                    scoreIf(features.whiteRatio > 0.28f, 0.18f) +
                    scoreIf(features.saturationMean < 0.16f, 0.06f)

            "notebook" ->
                keywordScore(hints, "paper", "book", "notebook", "cuaderno") +
                    scoreIf(features.whiteRatio > 0.22f, 0.14f) +
                    scoreIf(aspectRatio in 0.60f..1.80f, 0.08f)

            "newspaper" ->
                keywordScore(hints, "paper", "newspaper", "periodico", "text") +
                    scoreIf(features.whiteRatio > 0.22f, 0.12f) +
                    scoreIf(features.textureScore > 0.12f, 0.06f)

            "napkin_clean" ->
                keywordScore(hints, "napkin", "tissue", "paper", "servilleta") +
                    scoreIf(features.whiteRatio > 0.32f, 0.14f) +
                    scoreIf(features.brownRatio < 0.14f && features.redOrangeRatio < 0.12f, 0.06f)

            "napkin_used" ->
                keywordScore(hints, "napkin", "tissue", "servilleta", "food") +
                    scoreIf(features.brownRatio > 0.14f || features.redOrangeRatio > 0.10f, 0.16f) +
                    scoreIf(features.darkRatio > 0.16f || features.moistureProxy() > 0.18f, 0.08f)

            "foam_tray" ->
                keywordScore(hints, "foam", "tray", "plate", "icopor") +
                    scoreIf(features.whiteRatio > 0.40f, 0.16f) +
                    scoreIf(aspectRatio in 0.45f..1.70f, 0.08f)

            "delivery_container" ->
                keywordScore(hints, "container", "food", "box", "lunch", "delivery") +
                    scoreIf(aspectRatio in 0.45f..1.80f, 0.12f) +
                    scoreIf(features.whiteRatio > 0.16f || features.brownRatio > 0.14f, 0.08f)

            "tetra_pak" ->
                keywordScore(hints, "tetra pak", "carton", "milk", "juice", "box") +
                    scoreIf(aspectRatio in 0.75f..2.20f, 0.12f) +
                    scoreIf(features.whiteRatio > 0.20f && features.highlightRatio > 0.08f, 0.08f)

            "plastic_bag" ->
                keywordScore(hints, "bag", "plastic", "flexible package", "bolsa") +
                    scoreIf(features.textureScore > 0.14f, 0.10f) +
                    scoreIf(features.highlightRatio > 0.10f || features.whiteRatio > 0.16f, 0.08f)

            "snack_bag" ->
                keywordScore(hints, "bag", "wrapper", "snack", "package") +
                    scoreIf(features.highlightRatio > 0.18f, 0.14f) +
                    scoreIf(features.saturationMean > 0.20f, 0.08f)

            "multilayer_package" ->
                keywordScore(hints, "wrapper", "package", "flexible package", "food") +
                    scoreIf(features.highlightRatio > 0.14f, 0.12f) +
                    scoreIf(features.saturationMean > 0.18f || features.textureScore > 0.16f, 0.08f)

            "aluminum_foil" ->
                keywordScore(hints, "foil", "aluminum", "metal", "silver") +
                    scoreIf(features.highlightRatio > 0.20f && features.neutralRatio > 0.18f, 0.18f) +
                    scoreIf(features.textureScore > 0.16f, 0.08f)

            "disposable_cutlery" ->
                keywordScore(hints, "cutlery", "fork", "spoon", "knife") +
                    scoreIf(aspectRatio > 1.80f || aspectRatio < 0.45f, 0.12f) +
                    scoreIf(features.highlightRatio > 0.08f || features.whiteRatio > 0.16f, 0.04f)

            "lid" ->
                keywordScore(hints, "lid", "cap", "cover", "tapa") +
                    scoreIf(aspectRatio in 0.70f..1.40f, 0.10f)

            "fruit_peel" ->
                keywordScore(hints, "organic", "food", "fruit", "peel", "fruta") +
                    scoreIf(features.greenRatio > 0.14f || features.redOrangeRatio > 0.20f, 0.18f) +
                    scoreIf(features.textureScore > 0.14f, 0.06f)

            "vegetable_scraps" ->
                keywordScore(hints, "organic", "food", "vegetable", "scrap", "verdura") +
                    scoreIf(features.greenRatio > 0.18f || features.brownRatio > 0.12f, 0.16f) +
                    scoreIf(features.textureScore > 0.12f, 0.06f)

            "mixed_organic" ->
                keywordScore(hints, "organic", "food", "resto", "mixed") +
                    scoreIf(features.greenRatio > 0.10f || features.redOrangeRatio > 0.14f || features.brownRatio > 0.12f, 0.18f) +
                    scoreIf(features.textureScore > 0.12f, 0.06f)

            "bone" ->
                keywordScore(hints, "bone", "organic", "food") +
                    scoreIf(features.whiteRatio > 0.18f && features.textureScore > 0.12f, 0.14f)

            "coffee_filter" ->
                keywordScore(hints, "coffee", "filter", "paper", "organic") +
                    scoreIf(features.brownRatio > 0.12f || features.darkRatio > 0.18f, 0.14f) +
                    scoreIf(features.whiteRatio > 0.18f, 0.06f)

            COMMON_WASTE_ID ->
                0.16f +
                    scoreIf(features.textureScore > 0.10f || features.highlightRatio > 0.08f || features.brownRatio > 0.08f, 0.08f)

            else -> 0f
        }

        if (category.family == WasteFamily.ORGANIC && !hints.contains("organic") && features.greenRatio < 0.08f && features.redOrangeRatio < 0.08f && features.brownRatio < 0.08f) {
            score -= 0.12f
        }
        if (category.family == WasteFamily.CARDBOARD && features.whiteRatio > 0.56f && features.brownRatio < 0.10f) {
            score -= 0.08f
        }
        if (category.id == "paper_sheet" && features.brownRatio > 0.22f) {
            score -= 0.10f
        }
        if (category.id == "plastic_bag" && hints.contains("metal")) {
            score -= 0.10f
        }
        if (category.id == "can" && hints.contains("paper")) {
            score -= 0.12f
        }
        return score.coerceIn(0f, 1f)
    }

    private fun keywordScore(
        hints: Set<String>,
        vararg keywords: String
    ): Float {
        val matches = keywords.count { keyword -> hints.any { it.contains(keyword) } }
        return (matches * 0.14f).coerceAtMost(0.56f)
    }

    private fun scoreIf(condition: Boolean, score: Float): Float = if (condition) score else 0f

    private fun liquidScoreFor(
        slot: EvidenceSlot,
        hints: Set<String>,
        features: ImageFeatures
    ): Float {
        var score = features.highlightRatio * 0.26f + features.darkRatio * 0.18f + features.moistureProxy() * 0.22f
        if (slot == EvidenceSlot.INNER_VIEW || slot == EvidenceSlot.OPENING_NECK) score += 0.18f
        if (hints.any { it.contains("drink") || it.contains("water") || it.contains("juice") || it.contains("coffee") }) {
            score += 0.20f
        }
        return score
    }

    private fun residueScoreFor(
        hints: Set<String>,
        features: ImageFeatures
    ): Float {
        var score = features.brownRatio * 0.52f + features.redOrangeRatio * 0.38f + features.textureScore * 0.10f
        if (hints.any { it.contains("food") || it.contains("organic") || it.contains("pizza") || it.contains("sauce") }) {
            score += 0.22f
        }
        return score
    }

    private fun greaseScoreFor(
        hints: Set<String>,
        features: ImageFeatures
    ): Float {
        var score = features.brownRatio * 0.55f + features.highlightRatio * 0.22f + features.moistureProxy() * 0.10f
        if (hints.any { it.contains("pizza") || it.contains("food") || it.contains("fried") }) {
            score += 0.16f
        }
        return score
    }

    private fun moistureScoreFor(
        hints: Set<String>,
        features: ImageFeatures
    ): Float {
        var score = features.moistureProxy() * 0.52f + features.highlightRatio * 0.18f + features.darkRatio * 0.12f
        if (hints.any { it.contains("drink") || it.contains("juice") || it.contains("coffee") || it.contains("water") }) {
            score += 0.14f
        }
        return score
    }

    private fun organicScoreFor(
        hints: Set<String>,
        features: ImageFeatures,
        family: WasteFamily?
    ): Float {
        var score = 0f
        if (family == WasteFamily.ORGANIC) score += 0.26f
        if (hints.any { it.contains("organic") || it.contains("food") || it.contains("fruit") || it.contains("vegetable") || it.contains("peel") || it.contains("scrap") }) {
            score += 0.42f
        }
        score += features.greenRatio * 0.18f + features.redOrangeRatio * 0.12f + features.brownRatio * 0.08f
        return score
    }

    private fun occlusionScoreFor(detection: DetectionCandidate): Float {
        return (0.48f - detection.confidence).coerceAtLeast(0f) * 1.7f
    }

    private fun buildExplanationTokens(
        categoryName: String?,
        rawHints: List<String>,
        features: ImageFeatures,
        slot: EvidenceSlot
    ): List<String> {
        val tokens = mutableListOf<String>()
        if (categoryName != null) tokens += "Categoria estimada: $categoryName."
        if (slot != EvidenceSlot.OUTER_FULL) tokens += "Se analizo una vista guiada: ${slot.name.lowercase()}."
        if (features.cleanScore > 0.60f) tokens += "La superficie se ve relativamente limpia."
        if (features.brownRatio > 0.18f || features.redOrangeRatio > 0.18f) tokens += "Se observan manchas o restos adheridos."
        if (features.highlightRatio > 0.18f) tokens += "Hay reflejos o humedad que conviene revisar."
        if (features.textureScore > 0.18f) tokens += "La textura parece irregular o deformada."
        if (rawHints.isNotEmpty()) tokens += "Pistas visuales: ${rawHints.take(4).joinToString()}."
        return tokens
    }

    private fun normalizeText(value: String): String {
        return Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun String.containsAny(vararg options: String): Boolean {
        return options.any { contains(it) }
    }

    private companion object {
        const val COMMON_WASTE_ID = "common_waste"
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
    val neutralRatio: Float,
    val saturationMean: Float,
    val textureScore: Float,
    val blurScore: Float,
    val cleanScore: Float
) {
    companion object {
        fun fromBitmap(bitmap: Bitmap): ImageFeatures {
            val width = bitmap.width
            val height = bitmap.height
            val stepX = max(1, width / 42)
            val stepY = max(1, height / 42)

            var samples = 0
            var brightnessSum = 0f
            var saturationSum = 0f
            var whiteCount = 0
            var darkCount = 0
            var brownCount = 0
            var greenCount = 0
            var redOrangeCount = 0
            var highlightCount = 0
            var neutralCount = 0
            var edgeSum = 0f

            for (y in 0 until height step stepY) {
                for (x in 0 until width step stepX) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = Color.red(pixel) / 255f
                    val g = Color.green(pixel) / 255f
                    val b = Color.blue(pixel) / 255f
                    val brightness = 0.2126f * r + 0.7152f * g + 0.0722f * b
                    val maxChannel = max(r, max(g, b))
                    val minChannel = kotlin.math.min(r, kotlin.math.min(g, b))
                    val saturation = if (maxChannel == 0f) 0f else (maxChannel - minChannel) / maxChannel
                    brightnessSum += brightness
                    saturationSum += saturation
                    samples += 1

                    if (brightness > 0.78f && abs(r - g) < 0.12f && abs(g - b) < 0.12f) whiteCount += 1
                    if (brightness < 0.22f) darkCount += 1
                    if (r > 0.30f && r < 0.74f && g > 0.16f && g < 0.60f && b < 0.36f) brownCount += 1
                    if (g > r + 0.05f && g > b + 0.05f) greenCount += 1
                    if (r > 0.45f && g > 0.18f && g < 0.70f && b < 0.40f) redOrangeCount += 1
                    if (brightness > 0.88f) highlightCount += 1
                    if (abs(r - g) < 0.10f && abs(g - b) < 0.10f) neutralCount += 1

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
            val neutralRatio = neutralCount / sampleCount
            val saturationMean = saturationSum / sampleCount
            val sharpness = (edgeSum / sampleCount / 255f).coerceIn(0f, 1f)
            val blurScore = (1f - sharpness).coerceIn(0f, 1f)
            val cleanScore = (
                whiteRatio * 0.42f +
                    meanBrightness * 0.22f +
                    (1f - saturationMean) * 0.12f -
                    brownRatio * 0.32f -
                    darkRatio * 0.18f
                ).coerceIn(0f, 1f)

            return ImageFeatures(
                meanBrightness = meanBrightness,
                whiteRatio = whiteRatio,
                darkRatio = darkRatio,
                brownRatio = brownRatio,
                greenRatio = greenRatio,
                redOrangeRatio = redOrangeRatio,
                highlightRatio = highlightRatio,
                neutralRatio = neutralRatio,
                saturationMean = saturationMean,
                textureScore = sharpness,
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

private fun ImageFeatures.moistureProxy(): Float {
    return (highlightRatio * 0.55f + darkRatio * 0.25f + saturationMean * 0.20f).coerceIn(0f, 1f)
}
