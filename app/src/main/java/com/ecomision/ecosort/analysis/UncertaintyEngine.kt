package com.ecomision.ecosort.analysis

import com.ecomision.ecosort.model.EvidenceSlot
import com.ecomision.ecosort.model.ScanSession
import com.ecomision.ecosort.model.UncertaintyDecision
import com.ecomision.ecosort.model.WasteCategory
import kotlin.math.roundToInt

class UncertaintyEngine {

    fun evaluate(category: WasteCategory?, session: ScanSession): UncertaintyDecision {
        val observations = session.observations
        if (observations.isEmpty()) {
            return UncertaintyDecision(
                confidence = 0f,
                isCertain = false,
                missingSlots = category?.requiredViews.orEmpty(),
                warning = "Aun no se analizo ninguna vista."
            )
        }

        val avgClassifier = observations.map { it.classifierConfidence }.average().toFloat()
        val avgBlur = observations.map { it.blurScore }.average().toFloat()
        val avgOcclusion = observations.map { it.occlusionScore }.average().toFloat()

        val categoryVotes = observations.groupingBy { it.categoryId }.eachCount()
        val topVoteCount = categoryVotes.maxOfOrNull { it.value } ?: 0
        val stability = if (observations.isEmpty()) 0f else topVoteCount / observations.size.toFloat()
        val requiredViews = category?.requiredViews.orEmpty()
        val coverage = if (requiredViews.isEmpty()) 1f else {
            session.completedSlots.intersect(requiredViews.toSet()).size / requiredViews.size.toFloat()
        }

        val missingSlots = requiredViews.filterNot(session.completedSlots::contains)
        val missingCritical = missingSlots.filter {
            it in setOf(EvidenceSlot.INNER_VIEW, EvidenceSlot.OPENING_NECK, EvidenceSlot.BOTH_FACES)
        }

        val confidence = (
            avgClassifier * 0.45f +
                stability * 0.20f +
                coverage * 0.25f +
                (1f - avgBlur.coerceIn(0f, 1f)) * 0.05f +
                (1f - avgOcclusion.coerceIn(0f, 1f)) * 0.05f
            ).coerceIn(0f, 1f)

        val isCertain = confidence >= 0.78f && missingCritical.isEmpty()
        val warning = if (isCertain) {
            null
        } else {
            buildString {
                append("Confianza actual ${(confidence * 100).roundToInt()}%.")
                if (missingCritical.isNotEmpty()) {
                    append(" Falta ver: ${missingCritical.joinToString { it.name.lowercase() }}.")
                } else if (avgBlur > 0.55f) {
                    append(" La imagen se ve borrosa.")
                } else {
                    append(" Hace falta otra vista para confirmar.")
                }
            }
        }

        return UncertaintyDecision(
            confidence = confidence,
            isCertain = isCertain,
            missingSlots = missingSlots,
            warning = warning
        )
    }
}

