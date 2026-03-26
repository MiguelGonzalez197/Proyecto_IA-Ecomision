package com.ecomision.ecosort.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Texture
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ecomision.ecosort.model.DetectionCandidate
import com.ecomision.ecosort.model.EvidenceSlot
import com.ecomision.ecosort.model.GuidedViewInstruction
import com.ecomision.ecosort.ui.components.CameraPreview
import com.ecomision.ecosort.ui.components.EcoChip
import com.ecomision.ecosort.ui.components.EcoPanel
import com.ecomision.ecosort.ui.components.EcoPrimaryButton
import com.ecomision.ecosort.ui.components.ResultPanel
import com.ecomision.ecosort.ui.components.confidenceColor
import com.ecomision.ecosort.ui.theme.CameraBottomScrim
import com.ecomision.ecosort.ui.theme.CameraTopScrim
import com.ecomision.ecosort.ui.theme.EcoGreen
import com.ecomision.ecosort.ui.theme.EcoGreenLight
import com.ecomision.ecosort.ui.theme.EcoText
import com.ecomision.ecosort.ui.theme.EcoTextMuted
import com.ecomision.ecosort.ui.theme.WarningAmber

@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    var hasCameraPermission by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val selectedCandidate = remember(uiState.selectedCandidateId, uiState.detections) {
        uiState.detections.firstOrNull { it.id == uiState.selectedCandidateId }
    }
    val stage = remember(hasCameraPermission, uiState.isAnalyzing, uiState.currentInstruction, uiState.currentResult, uiState.detections.size) {
        resolveStage(
            hasPermission = hasCameraPermission,
            uiState = uiState
        )
    }
    val guidanceCue = remember(uiState.currentInstruction) {
        uiState.currentInstruction?.let(::guidanceCue)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            hasPermission = hasCameraPermission,
            analyzer = viewModel.frameAnalyzer,
            detections = uiState.detections,
            imageWidth = uiState.imageWidth,
            imageHeight = uiState.imageHeight,
            selectedCandidateId = uiState.selectedCandidateId,
            interactionEnabled = !uiState.isAnalyzing,
            onCandidateTapped = { candidate ->
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.onCandidateSelected(candidate)
            }
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(232.dp)
                .background(CameraTopScrim)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(340.dp)
                .background(CameraBottomScrim)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EcoPanel(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color.Black.copy(alpha = 0.24f),
                    borderColor = Color.White.copy(alpha = 0.10f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ScannerBrand(light = true)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EcoChip(
                                text = stage.title,
                                containerColor = stage.accent.copy(alpha = 0.22f),
                                contentColor = Color.White
                            )
                            EcoChip(
                                text = when (uiState.detections.size) {
                                    0 -> "Sin residuos visibles"
                                    1 -> "1 objeto listo"
                                    else -> "${uiState.detections.size} objetos listos"
                                },
                                containerColor = Color.White.copy(alpha = 0.10f),
                                contentColor = Color.White.copy(alpha = 0.92f)
                            )
                            EcoChip(
                                text = "Clasificados ${history.size}",
                                containerColor = Color.White.copy(alpha = 0.10f),
                                contentColor = Color.White.copy(alpha = 0.92f)
                            )
                        }
                        androidx.compose.material3.Text(
                            text = uiState.statusMessage,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.92f)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = hasCameraPermission && guidanceCue != null,
                    enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(220)),
                    exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(180))
                ) {
                    val cue = guidanceCue ?: return@AnimatedVisibility
                    EcoPanel(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = Color.Black.copy(alpha = 0.24f),
                        borderColor = Color.White.copy(alpha = 0.10f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(cue.accent.copy(alpha = 0.24f))
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = cue.icon,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                androidx.compose.material3.Text(
                                    text = cue.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                androidx.compose.material3.Text(
                                    text = cue.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.84f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (!hasCameraPermission) {
                PermissionCard(
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
            } else {
                ResultPanel(
                    result = uiState.currentResult,
                    instruction = uiState.currentInstruction,
                    statusMessage = uiState.statusMessage,
                    isAnalyzing = uiState.isAnalyzing,
                    selectedCandidate = selectedCandidate,
                    detectionCount = uiState.detections.size,
                    classifiedCount = history.size,
                    history = history
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    onRequestPermission: () -> Unit
) {
    EcoPanel(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        containerColor = Color.White.copy(alpha = 0.97f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ScannerBrand()
            androidx.compose.material3.Text(
                text = "Activa la camara para iniciar el clasificador en tiempo real.",
                style = MaterialTheme.typography.bodyLarge
            )
            androidx.compose.material3.Text(
                text = "La camara queda lista desde el inicio y los objetos se podran tocar apenas aparezcan en pantalla.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            EcoPrimaryButton(
                text = "Habilitar camara",
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private data class StagePresentation(
    val title: String,
    val accent: Color
)

private data class GuidanceCue(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color
)

@Composable
private fun ScannerBrand(
    light: Boolean = false
) {
    val titleColor = if (light) Color.White else EcoText
    val signatureColor = if (light) Color.White.copy(alpha = 0.78f) else EcoTextMuted
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "Ecomision - Clasificador",
            style = MaterialTheme.typography.titleLarge,
            color = titleColor
        )
        Text(
            text = "by fourwenteee",
            style = MaterialTheme.typography.labelMedium,
            color = signatureColor
        )
    }
}

private fun resolveStage(
    hasPermission: Boolean,
    uiState: ScannerUiState
): StagePresentation {
    if (!hasPermission) {
        return StagePresentation(title = "Permiso requerido", accent = WarningAmber)
    }
    return when {
        uiState.isAnalyzing -> StagePresentation("Clasificando", EcoGreenLight)
        uiState.currentResult != null -> StagePresentation(
            title = "Resultado listo",
            accent = confidenceColor(uiState.currentResult.confidence)
        )
        uiState.detections.isNotEmpty() -> StagePresentation("Deteccion en vivo", EcoGreen)
        else -> StagePresentation("Analizando escena", EcoGreen)
    }
}

private fun guidanceCue(
    instruction: GuidedViewInstruction
): GuidanceCue {
    val icon = when (instruction.slot) {
        EvidenceSlot.CLOSE_TEXTURE,
        EvidenceSlot.RIM_CLOSEUP,
        EvidenceSlot.OPENING_NECK -> Icons.Rounded.CenterFocusStrong

        EvidenceSlot.SEPARATED_BACKGROUND,
        EvidenceSlot.OUTER_FULL -> Icons.Rounded.Visibility

        EvidenceSlot.BOTH_FACES,
        EvidenceSlot.BACK_SIDE,
        EvidenceSlot.BOTTOM_VIEW -> Icons.Rounded.History

        else -> Icons.Rounded.Texture
    }
    return GuidanceCue(
        title = instruction.title,
        subtitle = instruction.description,
        icon = icon,
        accent = WarningAmber
    )
}
