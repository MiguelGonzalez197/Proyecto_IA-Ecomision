package com.ecomision.ecosort.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ecomision.ecosort.ui.components.CameraPreview
import com.ecomision.ecosort.ui.components.ResultPanel

@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFF5FBF7),
                        Color(0xFFE7F3EC),
                        Color(0xFFD9EEE4)
                    )
                )
            )
    ) {
        CameraPreview(
            hasPermission = hasCameraPermission,
            analyzer = viewModel.frameAnalyzer,
            detections = uiState.detections,
            imageWidth = uiState.imageWidth,
            imageHeight = uiState.imageHeight,
            selectedCandidateId = uiState.selectedCandidateId,
            onCandidateTapped = viewModel::onCandidateSelected
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "EcoSort",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Detecta residuos, toca uno, valida vistas faltantes y decide entre caneca blanca, negra o verde.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f)
                )
                uiState.detectorWarmupMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (!hasCameraPermission) {
                PermissionCard(
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
            }

            ResultPanel(
                result = uiState.currentResult,
                instruction = uiState.currentInstruction,
                statusMessage = uiState.statusMessage,
                roundsCompleted = uiState.roundsCompleted,
                sessionSummary = uiState.sessionSummary,
                onCaptureGuidedView = viewModel::captureGuidedView,
                onClearSelection = viewModel::clearSelection
            )
        }
    }
}

@Composable
private fun PermissionCard(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.92f), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Permiso de camara requerido",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "La app necesita acceso a la camara para detectar residuos en tiempo real.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(4.dp))
        Button(onClick = onRequestPermission) {
            Text("Habilitar camara")
        }
    }
}
