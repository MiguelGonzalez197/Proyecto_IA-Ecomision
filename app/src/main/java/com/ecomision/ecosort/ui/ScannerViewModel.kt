package com.ecomision.ecosort.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ecomision.ecosort.AppContainer
import com.ecomision.ecosort.analysis.BinRuleEngine
import com.ecomision.ecosort.analysis.BoundingBoxIsolationEngine
import com.ecomision.ecosort.analysis.GuidedViewPlanner
import com.ecomision.ecosort.analysis.UncertaintyEngine
import com.ecomision.ecosort.analysis.WasteHeuristicClassifier
import com.ecomision.ecosort.camera.CameraFrameAnalyzer
import com.ecomision.ecosort.camera.FrameAnalysisSnapshot
import com.ecomision.ecosort.camera.WasteObjectDetector
import com.ecomision.ecosort.model.BinType
import com.ecomision.ecosort.model.DetectionCandidate
import com.ecomision.ecosort.model.EvidenceSlot
import com.ecomision.ecosort.model.GuidedViewInstruction
import com.ecomision.ecosort.model.ScanSession
import com.ecomision.ecosort.model.WasteAnalysis
import com.ecomision.ecosort.model.WasteCategory
import com.ecomision.ecosort.repository.CatalogRepository
import com.ecomision.ecosort.repository.ScanHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScannerViewModel(
    private val catalogRepository: CatalogRepository,
    private val scanHistoryRepository: ScanHistoryRepository,
    wasteObjectDetector: WasteObjectDetector,
    private val isolationEngine: BoundingBoxIsolationEngine,
    private val classifier: WasteHeuristicClassifier,
    private val binRuleEngine: BinRuleEngine,
    private val uncertaintyEngine: UncertaintyEngine,
    private val guidedViewPlanner: GuidedViewPlanner
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var latestFrameBitmap: Bitmap? = null
    private var latestDetections: List<DetectionCandidate> = emptyList()
    private var selectedCandidate: DetectionCandidate? = null
    private var scanSession: ScanSession? = null

    val frameAnalyzer = CameraFrameAnalyzer(wasteObjectDetector, ::onFrameSnapshot)

    init {
        viewModelScope.launch {
            catalogRepository.seedIfNeeded()
            _uiState.update {
                it.copy(
                    detectorWarmupMessage = null,
                    statusMessage = "Toca uno de los residuos detectados para iniciar el analisis."
                )
            }
        }
    }

    fun onFrameSnapshot(snapshot: FrameAnalysisSnapshot) {
        latestFrameBitmap = snapshot.bitmap
        latestDetections = snapshot.detections
        val currentlySelectedId = _uiState.value.selectedCandidateId
        val refreshedSelected = currentlySelectedId?.let { id ->
            snapshot.detections.firstOrNull { it.id == id }
        }
        if (refreshedSelected != null) {
            selectedCandidate = refreshedSelected
        }

        _uiState.update {
            it.copy(
                detections = snapshot.detections,
                imageWidth = snapshot.imageWidth,
                imageHeight = snapshot.imageHeight,
                detectorWarmupMessage = if (snapshot.detections.isEmpty()) it.detectorWarmupMessage else null,
                statusMessage = when {
                    currentlySelectedId != null && refreshedSelected == null ->
                        "Objeto fuera de cuadro. Reencuadra el residuo seleccionado."

                    currentlySelectedId != null ->
                        it.statusMessage

                    snapshot.detections.isEmpty() ->
                        "Buscando residuos en escena..."

                    else ->
                        "Se detectaron ${snapshot.detections.size} residuos potenciales. Toca uno para analizar."
                }
            )
        }
    }

    fun onCandidateSelected(candidate: DetectionCandidate) {
        selectedCandidate = candidate
        scanSession = ScanSession(candidateId = candidate.id)
        _uiState.update {
            it.copy(
                selectedCandidateId = candidate.id,
                currentResult = null,
                currentInstruction = null,
                roundsCompleted = 0,
                isAnalyzing = true,
                statusMessage = "Analizando vista inicial del residuo..."
            )
        }
        analyzeSelection(slot = EvidenceSlot.OUTER_FULL)
    }

    fun captureGuidedView() {
        val instruction = _uiState.value.currentInstruction ?: return
        _uiState.update {
            it.copy(
                isAnalyzing = true,
                statusMessage = "Reanalizando con la vista guiada solicitada..."
            )
        }
        analyzeSelection(slot = instruction.slot)
    }

    fun clearSelection() {
        selectedCandidate = null
        scanSession = null
        _uiState.update {
            it.copy(
                selectedCandidateId = null,
                currentResult = null,
                currentInstruction = null,
                roundsCompleted = 0,
                statusMessage = "Selecciona otro residuo para analizar."
            )
        }
    }

    private fun analyzeSelection(slot: EvidenceSlot) {
        viewModelScope.launch {
            val frameBitmap = latestFrameBitmap
            val candidate = selectedCandidate
            if (frameBitmap == null || candidate == null) {
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        statusMessage = "No hay una vista valida del residuo. Reencuadra e intenta otra vez."
                    )
                }
                return@launch
            }

            val catalog = catalogRepository.getCategories()
            val rules = catalogRepository.getRules()
            val isolated = isolationEngine.isolate(frameBitmap, candidate.boundingBox)
            val observation = classifier.analyze(
                bitmap = isolated.bitmap,
                detection = candidate,
                slot = slot,
                catalog = catalog
            )

            val updatedSession = (scanSession ?: ScanSession(candidate.id)).add(observation)
            scanSession = updatedSession

            val dominantCategory = resolveDominantCategory(updatedSession, catalog, observation.categoryId)
            val decision = binRuleEngine.decide(dominantCategory, updatedSession, rules)
            val uncertainty = uncertaintyEngine.evaluate(dominantCategory, updatedSession)
            val shouldAskForMore = !uncertainty.isCertain && updatedSession.observations.size < 3
            val nextInstruction = if (shouldAskForMore) {
                guidedViewPlanner.plan(dominantCategory, updatedSession, uncertainty.missingSlots)
            } else {
                null
            }

            val result = WasteAnalysis(
                category = dominantCategory,
                probableBin = if (decision.binType == BinType.UNKNOWN) dominantCategory?.defaultBin ?: BinType.BLACK else decision.binType,
                confidence = uncertainty.confidence,
                reason = decision.reason,
                evidenceUsed = buildList {
                    addAll(decision.evidenceSummary)
                    addAll(observation.explanationTokens.take(3))
                },
                warning = if (shouldAskForMore) uncertainty.warning else uncertainty.warning?.takeIf { !uncertainty.isCertain },
                nextInstruction = nextInstruction,
                needsMoreEvidence = shouldAskForMore
            )

            _uiState.update {
                it.copy(
                    isAnalyzing = false,
                    currentResult = result,
                    currentInstruction = nextInstruction,
                    roundsCompleted = updatedSession.observations.size,
                    sessionSummary = buildSessionSummary(updatedSession, dominantCategory),
                    statusMessage = if (shouldAskForMore) {
                        "Necesito otra vista antes de confirmar la caneca."
                    } else {
                        "Analisis completado."
                    }
                )
            }

            if (!shouldAskForMore) {
                scanHistoryRepository.save(result)
            }
        }
    }

    private fun resolveDominantCategory(
        session: ScanSession,
        catalog: List<WasteCategory>,
        fallbackCategoryId: String?
    ): WasteCategory? {
        val weightedScores = mutableMapOf<String, Float>()
        session.observations.forEach { observation ->
            val categoryId = observation.categoryId ?: return@forEach
            weightedScores[categoryId] = (weightedScores[categoryId] ?: 0f) + observation.classifierConfidence
        }
        val bestId = weightedScores.maxByOrNull { it.value }?.key ?: fallbackCategoryId
        return catalog.firstOrNull { it.id == bestId }
    }

    private fun buildSessionSummary(session: ScanSession, category: WasteCategory?): String {
        val observedSlots = session.completedSlots.joinToString { it.name.lowercase() }
        return buildString {
            append("Categoria dominante: ${category?.displayName ?: "No confirmada"}. ")
            append("Vistas analizadas: $observedSlots.")
        }
    }

    override fun onCleared() {
        frameAnalyzer.shutdown()
        super.onCleared()
    }
}

class ScannerViewModelFactory(
    private val appContainer: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ScannerViewModel(
            catalogRepository = appContainer.catalogRepository,
            scanHistoryRepository = appContainer.scanHistoryRepository,
            wasteObjectDetector = appContainer.wasteObjectDetector,
            isolationEngine = appContainer.isolationEngine,
            classifier = appContainer.classifier,
            binRuleEngine = appContainer.binRuleEngine,
            uncertaintyEngine = appContainer.uncertaintyEngine,
            guidedViewPlanner = appContainer.guidedViewPlanner
        ) as T
    }
}
