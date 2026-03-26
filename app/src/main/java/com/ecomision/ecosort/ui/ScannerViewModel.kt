package com.ecomision.ecosort.ui

import android.graphics.Bitmap
import android.graphics.RectF
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
import com.ecomision.ecosort.model.BinRule
import com.ecomision.ecosort.model.BinType
import com.ecomision.ecosort.model.DetectionCandidate
import com.ecomision.ecosort.model.EvidenceSlot
import com.ecomision.ecosort.model.GuidedViewInstruction
import com.ecomision.ecosort.model.ScanSession
import com.ecomision.ecosort.model.UncertaintyDecision
import com.ecomision.ecosort.model.WasteAnalysis
import com.ecomision.ecosort.model.WasteCategory
import com.ecomision.ecosort.repository.CatalogRepository
import com.ecomision.ecosort.repository.ScanHistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max

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
    val history = scanHistoryRepository.observeRecent()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList()
        )

    private var latestFrameBitmap: Bitmap? = null
    private var latestDetections: List<DetectionCandidate> = emptyList()
    private var selectedCandidate: DetectionCandidate? = null
    private var selectedCandidateAnchor: RectF? = null
    private var scanSession: ScanSession? = null
    private var cachedCatalog: List<WasteCategory> = emptyList()
    private var cachedRules: List<BinRule> = emptyList()
    private var analysisJob: Job? = null
    private var analysisRequestId: Long = 0L

    val frameAnalyzer = CameraFrameAnalyzer(wasteObjectDetector, ::onFrameSnapshot)

    init {
        viewModelScope.launch {
            catalogRepository.seedIfNeeded()
            cachedCatalog = catalogRepository.getCategories()
            cachedRules = catalogRepository.getRules()
            _uiState.update {
                it.copy(
                    statusMessage = "Apunta la camara hacia un residuo para empezar."
                )
            }
        }
    }

    fun onFrameSnapshot(snapshot: FrameAnalysisSnapshot) {
        latestFrameBitmap = snapshot.bitmap
        latestDetections = snapshot.detections
        val currentState = _uiState.value
        val refreshedSelected = currentState.selectedCandidateId?.let { resolveSelectedCandidate(it, snapshot.detections) }
        if (refreshedSelected != null) {
            rememberSelection(refreshedSelected)
        }
        val visibleDetections = when {
            snapshot.detections.isNotEmpty() -> snapshot.detections
            currentState.selectedCandidateId != null && selectedCandidate?.id == currentState.selectedCandidateId -> listOfNotNull(selectedCandidate)
            else -> emptyList()
        }

        _uiState.update { state ->
            state.copy(
                detections = visibleDetections,
                imageWidth = snapshot.imageWidth,
                imageHeight = snapshot.imageHeight,
                selectedCandidateId = refreshedSelected?.id ?: state.selectedCandidateId,
                statusMessage = when {
                    state.isAnalyzing && state.selectedCandidateId != null ->
                        "Clasificando el objeto seleccionado..."

                    state.selectedCandidateId != null && refreshedSelected == null ->
                        "Ajusta el encuadre y vuelve a tocar el objeto para clasificarlo."

                    visibleDetections.isNotEmpty() && state.currentInstruction != null ->
                        "Ajusta la vista si quieres afinar el resultado o toca otro objeto."

                    visibleDetections.isNotEmpty() && state.currentResult != null ->
                        "Toca otro objeto destacado para clasificarlo."

                    visibleDetections.isNotEmpty() ->
                        "Toca uno de los objetos marcados para clasificarlo."

                    else ->
                        buildIdleStatusMessage(snapshot.sceneHint)
                }
            )
        }
    }

    fun onCandidateSelected(candidate: DetectionCandidate) {
        val continuingSession = shouldContinueSession(candidate)
        cancelActiveAnalysis()
        rememberSelection(candidate)

        if (!continuingSession) {
            scanSession = ScanSession(candidateId = candidate.id)
        }

        _uiState.update {
            it.copy(
                selectedCandidateId = candidate.id,
                currentResult = if (continuingSession) it.currentResult else null,
                currentInstruction = if (continuingSession) it.currentInstruction else null,
                sessionSummary = if (continuingSession) it.sessionSummary else "",
                isAnalyzing = true,
                statusMessage = if (continuingSession) {
                    "Reanalizando el objeto con la nueva vista..."
                } else {
                    "Clasificando el objeto seleccionado..."
                }
            )
        }

        val slot = if (continuingSession) {
            _uiState.value.currentInstruction?.slot ?: EvidenceSlot.OUTER_FULL
        } else {
            EvidenceSlot.OUTER_FULL
        }
        analyzeSelection(slot = slot)
    }

    fun clearSelection() {
        cancelActiveAnalysis()
        selectedCandidate = null
        selectedCandidateAnchor = null
        scanSession = null
        _uiState.update {
            it.copy(
                selectedCandidateId = null,
                currentResult = null,
                currentInstruction = null,
                sessionSummary = "",
                isAnalyzing = false,
                statusMessage = buildIdleStatusMessage()
            )
        }
    }

    private fun analyzeSelection(slot: EvidenceSlot) {
        val requestId = startAnalysisRequest()
        analysisJob = viewModelScope.launch {
            try {
                val frameBitmap = latestFrameBitmap
                val candidate = resolveCandidateForAnalysis()
                if (frameBitmap == null || candidate == null) {
                    if (!isLatestAnalysis(requestId)) return@launch
                    _uiState.update {
                        it.copy(
                            selectedCandidateId = null,
                            isAnalyzing = false,
                            statusMessage = "No pude aislar el objeto. Acercate y vuelve a tocarlo."
                        )
                    }
                    return@launch
                }

                val catalog = getCatalog()
                val rules = getRules()
                val isolated = isolationEngine.isolate(frameBitmap, candidate.boundingBox)
                val observation = classifier.analyze(
                    bitmap = isolated.bitmap,
                    detection = candidate,
                    slot = slot,
                    catalog = catalog
                )

                val updatedSession = (scanSession ?: ScanSession(candidate.id)).add(observation)
                val dominantCategory = resolveDominantCategory(updatedSession, catalog, observation.categoryId)
                val decision = binRuleEngine.decide(dominantCategory, updatedSession, rules)
                val uncertainty = uncertaintyEngine.evaluate(dominantCategory, updatedSession)
                val guidanceInstruction = guidanceFor(uncertainty, updatedSession, dominantCategory)
                val result = WasteAnalysis(
                    category = dominantCategory,
                    probableBin = if (decision.binType == BinType.UNKNOWN) {
                        dominantCategory?.defaultBin ?: BinType.BLACK
                    } else {
                        decision.binType
                    },
                    confidence = uncertainty.confidence,
                    reason = decision.reason,
                    evidenceUsed = buildList {
                        addAll(decision.evidenceSummary)
                        addAll(observation.explanationTokens.take(3))
                    },
                    warning = uncertainty.warning,
                    nextInstruction = guidanceInstruction,
                    needsMoreEvidence = guidanceInstruction != null
                )

                if (!isLatestAnalysis(requestId)) return@launch
                scanSession = updatedSession
                selectedCandidate = null
                _uiState.update {
                    it.copy(
                        selectedCandidateId = null,
                        isAnalyzing = false,
                        currentResult = result,
                        currentInstruction = guidanceInstruction,
                        sessionSummary = buildSessionSummary(updatedSession, dominantCategory),
                        statusMessage = if (guidanceInstruction != null) {
                            "Clasificacion tentativa lista. Ajusta la vista y toca de nuevo si quieres afinarla."
                        } else {
                            "Clasificacion lista. Toca otro objeto para continuar."
                        }
                    )
                }

                if (isLatestAnalysis(requestId)) {
                    scanHistoryRepository.save(result)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (!isLatestAnalysis(requestId)) return@launch
                _uiState.update {
                    it.copy(
                        selectedCandidateId = null,
                        isAnalyzing = false,
                        statusMessage = "No pude clasificar esta vista. Mejora el encuadre y vuelve a tocar el objeto."
                    )
                }
            }
        }
    }

    private fun guidanceFor(
        uncertainty: UncertaintyDecision,
        session: ScanSession,
        category: WasteCategory?
    ): GuidedViewInstruction? {
        val lowConfidence = uncertainty.confidence < 0.58f
        val needsAnotherAngle = uncertainty.missingSlots.isNotEmpty() && session.observations.size < 2
        if (!lowConfidence && !needsAnotherAngle) return null
        return guidedViewPlanner.plan(
            category = category,
            session = session,
            missingSlots = uncertainty.missingSlots
        )
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

    private fun buildSessionSummary(
        session: ScanSession,
        category: WasteCategory?
    ): String {
        val observedSlots = session.completedSlots.joinToString { it.name.lowercase() }
        return buildString {
            append("Tipo estimado: ${category?.displayName ?: "No confirmado"}. ")
            append("Vistas usadas: ${observedSlots.ifBlank { "vista principal" }}.")
        }
    }

    private fun rememberSelection(candidate: DetectionCandidate) {
        selectedCandidate = candidate
        selectedCandidateAnchor = RectF(candidate.boundingBox)
    }

    private fun shouldContinueSession(candidate: DetectionCandidate): Boolean {
        val currentInstruction = _uiState.value.currentInstruction ?: return false
        val currentSession = scanSession ?: return false
        if (currentInstruction.slot in currentSession.completedSlots) return false
        val anchor = selectedCandidateAnchor ?: return false
        return currentSession.candidateId == candidate.id || selectionScore(anchor, candidate.boundingBox) > 0.34f
    }

    private fun resolveCandidateForAnalysis(): DetectionCandidate? {
        val selectedId = _uiState.value.selectedCandidateId ?: selectedCandidate?.id ?: return null
        val liveCandidate = resolveSelectedCandidate(selectedId, latestDetections)
        if (liveCandidate != null) {
            rememberSelection(liveCandidate)
            if (_uiState.value.selectedCandidateId != liveCandidate.id) {
                _uiState.update { it.copy(selectedCandidateId = liveCandidate.id) }
            }
            return liveCandidate
        }
        return selectedCandidate?.let { candidate ->
            latestFrameBitmap?.let { bitmap ->
                candidate.copy(
                    boundingBox = RectF(
                        candidate.boundingBox.left.coerceIn(0f, bitmap.width.toFloat()),
                        candidate.boundingBox.top.coerceIn(0f, bitmap.height.toFloat()),
                        candidate.boundingBox.right.coerceIn(0f, bitmap.width.toFloat()),
                        candidate.boundingBox.bottom.coerceIn(0f, bitmap.height.toFloat())
                    )
                )
            }
        }
    }

    private fun resolveSelectedCandidate(
        selectedId: Long,
        detections: List<DetectionCandidate>
    ): DetectionCandidate? {
        detections.firstOrNull { it.id == selectedId }?.let { return it }
        val anchor = selectedCandidateAnchor ?: selectedCandidate?.boundingBox ?: return null
        return detections
            .map { candidate -> candidate to selectionScore(anchor, candidate.boundingBox) }
            .filter { (_, score) -> score > 0.18f }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun selectionScore(anchor: RectF, candidate: RectF): Float {
        val overlap = intersectionOverUnion(anchor, candidate)
        val centerDistance = hypot(
            (anchor.centerX() - candidate.centerX()).toDouble(),
            (anchor.centerY() - candidate.centerY()).toDouble()
        ).toFloat()
        val distanceThreshold = max(
            max(anchor.width(), anchor.height()),
            max(candidate.width(), candidate.height())
        ).coerceAtLeast(1f) * 2f
        val distanceScore = 1f - (centerDistance / distanceThreshold).coerceIn(0f, 1f)
        return overlap * 0.75f + distanceScore * 0.25f
    }

    private fun intersectionOverUnion(first: RectF, second: RectF): Float {
        val intersectionLeft = max(first.left, second.left)
        val intersectionTop = max(first.top, second.top)
        val intersectionRight = minOf(first.right, second.right)
        val intersectionBottom = minOf(first.bottom, second.bottom)
        val intersectionWidth = (intersectionRight - intersectionLeft).coerceAtLeast(0f)
        val intersectionHeight = (intersectionBottom - intersectionTop).coerceAtLeast(0f)
        val intersectionArea = intersectionWidth * intersectionHeight
        if (intersectionArea <= 0f) return 0f

        val firstArea = first.width().coerceAtLeast(0f) * first.height().coerceAtLeast(0f)
        val secondArea = second.width().coerceAtLeast(0f) * second.height().coerceAtLeast(0f)
        val unionArea = (firstArea + secondArea - intersectionArea).coerceAtLeast(1f)
        return (intersectionArea / unionArea).coerceIn(0f, 1f)
    }

    private fun buildIdleStatusMessage(
        sceneHint: String? = null
    ): String {
        return sceneHint ?: "Apunta la camara hacia un residuo o toca directamente el area que quieras analizar."
    }

    private suspend fun getCatalog(): List<WasteCategory> {
        if (cachedCatalog.isEmpty()) {
            cachedCatalog = catalogRepository.getCategories()
        }
        return cachedCatalog
    }

    private suspend fun getRules(): List<BinRule> {
        if (cachedRules.isEmpty()) {
            cachedRules = catalogRepository.getRules()
        }
        return cachedRules
    }

    private fun startAnalysisRequest(): Long {
        analysisJob?.cancel()
        analysisRequestId += 1
        return analysisRequestId
    }

    private fun cancelActiveAnalysis() {
        analysisJob?.cancel()
        analysisJob = null
        analysisRequestId += 1
    }

    private fun isLatestAnalysis(requestId: Long): Boolean = requestId == analysisRequestId

    override fun onCleared() {
        cancelActiveAnalysis()
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
