package com.ecomision.ecosort.ui

import com.ecomision.ecosort.model.DetectionCandidate
import com.ecomision.ecosort.model.GuidedViewInstruction
import com.ecomision.ecosort.model.WasteAnalysis

data class ScannerUiState(
    val detections: List<DetectionCandidate> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val selectedCandidateId: Long? = null,
    val statusMessage: String = "Analizando la escena...",
    val isAnalyzing: Boolean = false,
    val currentResult: WasteAnalysis? = null,
    val currentInstruction: GuidedViewInstruction? = null,
    val sessionSummary: String = ""
)
