package com.ecomision.ecosort.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ecomision.ecosort.data.db.entity.ScanHistoryEntity
import com.ecomision.ecosort.model.BinType
import com.ecomision.ecosort.ui.components.EcoChip
import com.ecomision.ecosort.ui.components.EcoConfidenceMeter
import com.ecomision.ecosort.ui.components.EcoPanel
import com.ecomision.ecosort.ui.components.EcoWordmark
import com.ecomision.ecosort.ui.theme.BinBlack
import com.ecomision.ecosort.ui.theme.BinGreen
import com.ecomision.ecosort.ui.theme.BinWhite
import com.ecomision.ecosort.ui.theme.EcoSurfaceAlt
import com.ecomision.ecosort.ui.theme.EcoText
import com.ecomision.ecosort.ui.theme.EcoTextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    history: List<ScanHistoryEntity>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EcoWordmark()
                androidx.compose.material3.Text(
                    text = "Historial reciente de clasificaciones guardadas on-device.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = EcoTextMuted
                )
            }
        }

        if (history.isEmpty()) {
            item {
                EcoPanel(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color.White.copy(alpha = 0.96f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        androidx.compose.material3.Text(
                            text = "Aun no hay escaneos guardados",
                            style = MaterialTheme.typography.titleLarge,
                            color = EcoText
                        )
                        androidx.compose.material3.Text(
                            text = "Cuando completes una clasificacion, aparecera aqui con su confianza y la caneca sugerida.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EcoTextMuted
                        )
                    }
                }
            }
        } else {
            items(history, key = { it.id }) { item ->
                HistoryCard(item = item)
            }
        }
    }
}

@Composable
private fun HistoryCard(
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
        containerColor = Color.White.copy(alpha = 0.96f)
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    androidx.compose.material3.Text(
                        text = formatCategoryName(item.categoryId),
                        style = MaterialTheme.typography.titleLarge,
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

            EcoConfidenceMeter(confidence = item.confidence)

            androidx.compose.material3.Text(
                text = item.reason,
                style = MaterialTheme.typography.bodyLarge,
                color = EcoText
            )

            if (item.evidenceSummary.isNotBlank()) {
                EcoChip(
                    text = item.evidenceSummary.replace(" | ", " • "),
                    containerColor = EcoSurfaceAlt,
                    contentColor = EcoTextMuted
                )
            }
        }
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
