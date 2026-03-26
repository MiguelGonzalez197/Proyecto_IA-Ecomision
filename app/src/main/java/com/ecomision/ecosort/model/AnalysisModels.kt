package com.ecomision.ecosort.model

data class GuidedViewInstruction(
    val slot: EvidenceSlot,
    val title: String,
    val description: String,
    val actionLabel: String = "Analizar vista"
)

data class WasteObservation(
    val slot: EvidenceSlot,
    val candidateId: Long,
    val categoryId: String?,
    val family: WasteFamily,
    val classifierConfidence: Float,
    val labels: List<String>,
    val cleanScore: Float,
    val liquidScore: Float,
    val residueScore: Float,
    val greaseScore: Float,
    val moistureScore: Float,
    val organicScore: Float,
    val blurScore: Float,
    val occlusionScore: Float,
    val explanationTokens: List<String>
)

data class ScanSession(
    val candidateId: Long,
    val observations: List<WasteObservation> = emptyList(),
    val completedSlots: Set<EvidenceSlot> = emptySet()
) {
    fun add(observation: WasteObservation): ScanSession {
        return copy(
            observations = observations + observation,
            completedSlots = completedSlots + observation.slot
        )
    }
}

data class BinDecision(
    val binType: BinType,
    val reason: String,
    val matchedRuleId: String,
    val evidenceSummary: List<String>
)

data class UncertaintyDecision(
    val confidence: Float,
    val isCertain: Boolean,
    val missingSlots: List<EvidenceSlot>,
    val warning: String?
)

data class WasteAnalysis(
    val category: WasteCategory?,
    val probableBin: BinType,
    val confidence: Float,
    val reason: String,
    val evidenceUsed: List<String>,
    val warning: String? = null,
    val nextInstruction: GuidedViewInstruction? = null,
    val needsMoreEvidence: Boolean = false
)

