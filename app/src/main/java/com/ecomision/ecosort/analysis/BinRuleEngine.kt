package com.ecomision.ecosort.analysis

import com.ecomision.ecosort.model.BinDecision
import com.ecomision.ecosort.model.BinRule
import com.ecomision.ecosort.model.BinType
import com.ecomision.ecosort.model.EvidenceSlot
import com.ecomision.ecosort.model.ScanSession
import com.ecomision.ecosort.model.WasteCategory
import kotlin.math.roundToInt

class BinRuleEngine {

    fun decide(
        category: WasteCategory?,
        session: ScanSession,
        rules: List<BinRule>
    ): BinDecision {
        val observations = session.observations
        if (observations.isEmpty()) {
            return BinDecision(
                binType = BinType.UNKNOWN,
                reason = "Todavia no hay evidencia suficiente para decidir.",
                matchedRuleId = "no_evidence",
                evidenceSummary = emptyList()
            )
        }

        val avgClean = observations.map { it.cleanScore }.average().toFloat()
        val maxLiquid = observations.maxOf { it.liquidScore }
        val maxResidue = observations.maxOf { it.residueScore }
        val maxGrease = observations.maxOf { it.greaseScore }
        val maxMoisture = observations.maxOf { it.moistureScore }
        val maxOrganic = observations.maxOf { it.organicScore }
        val viewedInside = session.completedSlots.any {
            it == EvidenceSlot.INNER_VIEW || it == EvidenceSlot.OPENING_NECK
        }

        val signals = mapOf(
            "organic_confident" to (maxOrganic > 0.7f && category?.family?.name == "ORGANIC"),
            "visible_liquid" to (maxLiquid > 0.58f),
            "strong_food_residue" to (maxResidue > 0.60f),
            "grease_visible" to (maxGrease > 0.56f),
            "used_or_wet" to (maxMoisture > 0.55f || maxResidue > 0.48f),
            "clean_and_dry" to (avgClean > 0.60f && maxLiquid < 0.28f && maxResidue < 0.35f && maxMoisture < 0.40f),
            "clean_and_drained" to (viewedInside && avgClean > 0.56f && maxLiquid < 0.32f),
            "default" to true
        )

        val matchingRule = rules
            .sortedByDescending { it.priority }
            .firstOrNull { rule ->
                (rule.categoryId == "*" || rule.categoryId == category?.id) &&
                    (signals[rule.conditions] == true)
            }

        val resolvedRule = matchingRule ?: BinRule(
            id = "fallback",
            categoryId = "*",
            priority = 0,
            conditions = "default",
            binType = category?.defaultBin ?: BinType.BLACK,
            reason = "Se usa la regla por defecto de la categoria."
        )

        val evidence = buildList {
            add("Limpieza estimada: ${(avgClean * 100).roundToInt()}%.")
            add("Restos visibles: ${(maxResidue * 100).roundToInt()}%.")
            add("Liquido visible: ${(maxLiquid * 100).roundToInt()}%.")
            add("Grasa visible: ${(maxGrease * 100).roundToInt()}%.")
            add("Vistas usadas: ${session.completedSlots.joinToString { it.name.lowercase() }}.")
        }

        return BinDecision(
            binType = resolvedRule.binType,
            reason = resolvedRule.reason,
            matchedRuleId = resolvedRule.id,
            evidenceSummary = evidence
        )
    }
}

