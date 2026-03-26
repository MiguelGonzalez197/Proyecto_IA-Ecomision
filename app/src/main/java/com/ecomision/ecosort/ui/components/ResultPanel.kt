package com.ecomision.ecosort.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ecomision.ecosort.model.BinType
import com.ecomision.ecosort.model.GuidedViewInstruction
import com.ecomision.ecosort.model.WasteAnalysis
import com.ecomision.ecosort.ui.theme.BinBlack
import com.ecomision.ecosort.ui.theme.BinGreen
import com.ecomision.ecosort.ui.theme.BinWhite
import com.ecomision.ecosort.ui.theme.EcoGreen
import com.ecomision.ecosort.ui.theme.WarningAmber

@Composable
fun ResultPanel(
    result: WasteAnalysis?,
    instruction: GuidedViewInstruction?,
    statusMessage: String,
    roundsCompleted: Int,
    sessionSummary: String,
    onCaptureGuidedView: () -> Unit,
    onClearSelection: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.94f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "EcoSort MVP",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    )
                }
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("Ronda $roundsCompleted") },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.secondary,
                        disabledLabelColor = MaterialTheme.colorScheme.onSecondary
                    )
                )
            }

            if (sessionSummary.isNotBlank()) {
                Text(
                    text = sessionSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }

            if (result != null) {
                BinBadge(binType = result.probableBin)
                LinearProgressIndicator(
                    progress = { result.confidence.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = EcoGreen,
                    trackColor = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Categoria: ${result.category?.displayName ?: "No confirmada"}",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = result.reason,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                result.warning?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = WarningAmber
                    )
                }
                result.evidenceUsed.take(4).forEach { line ->
                    Text(
                        text = "- $line",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    )
                }
            }

            if (instruction != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = instruction.title,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = instruction.description,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = onCaptureGuidedView,
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(instruction.actionLabel)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onClearSelection,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Limpiar")
                }
                if (instruction == null) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onClearSelection,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("Nuevo objeto")
                    }
                }
            }
        }
    }
}

@Composable
private fun BinBadge(binType: BinType) {
    val background = when (binType) {
        BinType.WHITE -> BinWhite
        BinType.BLACK -> BinBlack
        BinType.GREEN -> BinGreen
        BinType.UNKNOWN -> Color.LightGray
    }
    val foreground = if (binType == BinType.WHITE) Color.Black else Color.White

    Box(
        modifier = Modifier
            .background(background, CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = binType.displayName,
            color = foreground,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
