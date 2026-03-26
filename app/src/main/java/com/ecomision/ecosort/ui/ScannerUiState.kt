package com.ecomision.ecosort.ui

import com.ecomision.ecosort.model.DetectionCandidate
import com.ecomision.ecosort.model.GuidedViewInstruction
import com.ecomision.ecosort.model.WasteAnalysis

data class ScannerUiState(
    val detections: List<DetectionCandidate> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val selectedCandidateId: Long? = null,
    val statusMessage: String = "Apunta la camara a un residuo y toca el objeto a analizar.",
    val isAnalyzing: Boolean = false,
    val currentResult: WasteAnalysis? = null,
    val currentInstruction: GuidedViewInstruction? = null,
    val roundsCompleted: Int = 0,
    val detectorWarmupMessage: String? = "Inicializando detector on-device...",
    val sessionSummary: String = ""
)

