package com.ecomision.ecosort.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ecomision.ecosort.data.db.entity.ScanHistoryEntity
import com.ecomision.ecosort.model.BinType
import com.ecomision.ecosort.model.DetectionCandidate
import com.ecomision.ecosort.model.GuidedViewInstruction
import com.ecomision.ecosort.model.WasteAnalysis
import com.ecomision.ecosort.ui.theme.BinBlack
import com.ecomision.ecosort.ui.theme.BinGreen
import com.ecomision.ecosort.ui.theme.BinWhite
import com.ecomision.ecosort.ui.theme.EcoGreen
import com.ecomision.ecosort.ui.theme.EcoSurfaceAlt
import com.ecomision.ecosort.ui.theme.EcoText
import com.ecomision.ecosort.ui.theme.EcoTextMuted
import com.ecomision.ecosort.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ResultPanel(
    result: WasteAnalysis?,
    instruction: GuidedViewInstruction?,
    statusMessage: String,
    isAnalyzing: Boolean,
    selectedCandidate: DetectionCandidate?,
    detectionCount: Int,
    classifiedCount: Int,
    history: List<ScanHistoryEntity>
) {
    var historyExpanded by rememberSaveable { mutableStateOf(false) }

    EcoPanel(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        containerColor = Color.White.copy(alpha = 0.97f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EcoChip(
                        text = "Clasificador en vivo",
                        containerColor = EcoSurfaceAlt,
                        contentColor = EcoText
                    )
                    androidx.compose.material3.Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = EcoText
                    )
                }
                EcoChip(
                    text = "Objetos clasificados $classifiedCount",
                    containerColor = EcoSurfaceAlt,
                    contentColor = EcoGreen
                )
            }

            if (selectedCandidate != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EcoChip(
                        text = selectedCandidate.labels.firstOrNull()?.text ?: "Objeto seleccionado",
                        containerColor = EcoSurfaceAlt,
                        contentColor = EcoText
                    )
                    EcoChip(
                        text = "$detectionCount en escena",
                        containerColor = EcoSurfaceAlt,
                        contentColor = EcoTextMuted
                    )
                }
            }

            if (isAnalyzing) {
                EcoDivider()
                EcoLoader(
                    text = "Clasificando el objeto tocado...",
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (result != null) {
                EcoDivider()
                ResultSummary(result = result)
            } else if (detectionCount > 0) {
                EcoDivider()
                HelperCard(
                    title = "Objetos listos para tocar",
                    description = "Los contornos punteados indican que el sistema encontro regiones clasificables. Toca cualquiera para obtener caneca, tipo y confianza."
                )
            } else {
                EcoDivider()
                HelperCard(
                    title = "Esperando residuos en escena",
                    description = "Cuando aparezca un objeto claro, la app marcara el contorno y te dejara tocarlo directamente."
                )
            }

            if (instruction != null) {
                EcoDivider()
                GuidanceCard(instruction = instruction)
            }

            EcoDivider()
            HistorySection(
                history = history,
                classifiedCount = classifiedCount,
                expanded = historyExpanded,
                onToggle = { historyExpanded = !historyExpanded }
            )

            androidx.compose.material3.Text(
                text = "by fourwenteee",
                style = MaterialTheme.typography.labelMedium,
                color = EcoTextMuted
            )
        }
    }
}

@Composable
private fun ResultSummary(
    result: WasteAnalysis
) {
    val accent = confidenceColor(result.confidence)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.material3.Text(
                    text = result.category?.displayName ?: "Categoria no confirmada",
                    style = MaterialTheme.typography.titleLarge,
                    color = EcoText
                )
                androidx.compose.material3.Text(
                    text = result.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = EcoTextMuted
                )
            }
            BinBadge(binType = result.probableBin)
        }

        EcoConfidenceMeter(
            confidence = result.confidence,
            modifier = Modifier.fillMaxWidth()
        )

        if (result.warning != null) {
            EcoPanel(
                modifier = Modifier.fillMaxWidth(),
                containerColor = accent.copy(alpha = 0.12f),
                borderColor = accent.copy(alpha = 0.24f)
            ) {
                androidx.compose.material3.Text(
                    text = result.warning,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = EcoText
                )
            }
        }

        result.evidenceUsed.take(3).forEach { evidence ->
            EcoChip(
                text = evidence,
                containerColor = EcoSurfaceAlt,
                contentColor = EcoTextMuted
            )
        }
    }
}

@Composable
private fun HelperCard(
    title: String,
    description: String
) {
    EcoPanel(
        modifier = Modifier.fillMaxWidth(),
        containerColor = EcoSurfaceAlt.copy(alpha = 0.9f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.material3.Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = EcoText
            )
            androidx.compose.material3.Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = EcoTextMuted
            )
        }
    }
}

@Composable
private fun GuidanceCard(
    instruction: GuidedViewInstruction
) {
    EcoPanel(
        modifier = Modifier.fillMaxWidth(),
        containerColor = WarningAmber.copy(alpha = 0.12f),
        borderColor = WarningAmber.copy(alpha = 0.25f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EcoChip(
                text = "Sugerencia para mejorar la certeza",
                containerColor = WarningAmber.copy(alpha = 0.16f),
                contentColor = EcoText
            )
            androidx.compose.material3.Text(
                text = instruction.title,
                style = MaterialTheme.typography.titleLarge,
                color = EcoText
            )
            androidx.compose.material3.Text(
                text = instruction.description,
                style = MaterialTheme.typography.bodyLarge,
                color = EcoText
            )
            androidx.compose.material3.Text(
                text = "Ajusta la vista y toca de nuevo el mismo objeto solo si quieres afinar el resultado.",
                style = MaterialTheme.typography.bodyMedium,
                color = EcoTextMuted
            )
        }
    }
}

@Composable
private fun HistorySection(
    history: List<ScanHistoryEntity>,
    classifiedCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(EcoSurfaceAlt, CircleShape)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = EcoText
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    androidx.compose.material3.Text(
                        text = "Historial desplegable",
                        style = MaterialTheme.typography.titleMedium,
                        color = EcoText
                    )
                    androidx.compose.material3.Text(
                        text = "Objetos clasificados: $classifiedCount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EcoTextMuted
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = EcoTextMuted
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (history.isEmpty()) {
                    HelperCard(
                        title = "Aun no hay clasificaciones",
                        description = "Los objetos que toques y se clasifiquen apareceran aqui automaticamente."
                    )
                } else {
                    history.take(4).forEach { item ->
                        HistoryItem(item = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    item: ScanHistoryEntity
) {
    val formattedDate = remember(item.createdAtEpochMs) {
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(item.createdAtEpochMs))
    }
    val binType = runCatching { BinType.valueOf(item.binType) }.getOrDefault(BinType.UNKNOWN)
    val binBackground = when (binType) {
        BinType.WHITE -> BinWhite
        BinType.BLACK -> BinBlack
        BinType.GREEN -> BinGreen
        BinType.UNKNOWN -> EcoSurfaceAlt
    }
    val binTextColor = if (binType == BinType.WHITE || binType == BinType.UNKNOWN) EcoText else Color.White

    EcoPanel(
        modifier = Modifier.fillMaxWidth(),
        containerColor = EcoSurfaceAlt.copy(alpha = 0.78f),
        borderColor = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    androidx.compose.material3.Text(
                        text = formatCategoryName(item.categoryId),
                        style = MaterialTheme.typography.titleMedium,
                        color = EcoText
                    )
                    androidx.compose.material3.Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodyMedium,
                        color = EcoTextMuted
                    )
                }
                EcoChip(
                    text = binType.displayName,
                    containerColor = binBackground,
                    contentColor = binTextColor
                )
            }
            androidx.compose.material3.Text(
                text = item.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = EcoTextMuted
            )
        }
    }
}

@Composable
private fun BinBadge(
    binType: BinType
) {
    val background = when (binType) {
        BinType.WHITE -> BinWhite
        BinType.BLACK -> BinBlack
        BinType.GREEN -> BinGreen
        BinType.UNKNOWN -> EcoSurfaceAlt
    }
    val foreground = if (binType == BinType.WHITE || binType == BinType.UNKNOWN) EcoText else Color.White

    Box(
        modifier = Modifier
            .background(background, CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = binType.displayName,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = foreground
        )
    }
}

private fun formatCategoryName(categoryId: String?): String {
    if (categoryId.isNullOrBlank()) return "Categoria no confirmada"
    return categoryId
        .split("_")
        .joinToString(" ") { token ->
            token.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
}
