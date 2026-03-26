package com.ecomision.ecosort.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ecomision.ecosort.ui.components.EcoChip
import com.ecomision.ecosort.ui.components.EcoColorSwatch
import com.ecomision.ecosort.ui.components.EcoPanel
import com.ecomision.ecosort.ui.components.EcoPrimaryButton
import com.ecomision.ecosort.ui.components.EcoSectionCard
import com.ecomision.ecosort.ui.components.EcoWordmark
import com.ecomision.ecosort.ui.theme.EcoGreen
import com.ecomision.ecosort.ui.theme.EcoGreenDark
import com.ecomision.ecosort.ui.theme.EcoGreenLight
import com.ecomision.ecosort.ui.theme.EcoSurfaceAlt
import com.ecomision.ecosort.ui.theme.EcoText
import com.ecomision.ecosort.ui.theme.EcoTextMuted
import com.ecomision.ecosort.ui.theme.ErrorRed
import com.ecomision.ecosort.ui.theme.WarningAmber

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutScreen(
    onOpenScanner: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            EcoPanel(
                modifier = Modifier.fillMaxWidth(),
                containerColor = Color.White.copy(alpha = 0.97f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    EcoWordmark()
                    androidx.compose.material3.Text(
                        text = "Producto visualmente premium, minimalista y orientado a clasificacion ecologica precisa.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = EcoText
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EcoChip(text = "Tecnologia")
                        EcoChip(text = "Sostenibilidad")
                        EcoChip(text = "Precision")
                        EcoChip(text = "On-device")
                    }
                    EcoPrimaryButton(
                        text = "Abrir camara",
                        onClick = onOpenScanner,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            EcoSectionCard(
                title = "Principios visuales",
                subtitle = "Orden tipo Apple, identidad propia y lenguaje ecologico-tecnologico."
            ) {
                AboutPrinciple(
                    icon = Icons.Rounded.Verified,
                    title = "Claridad",
                    description = "Jerarquia tipografica limpia, paneles ligeros y foco en la accion principal."
                )
                AboutPrinciple(
                    icon = Icons.Rounded.Bolt,
                    title = "Fluidez",
                    description = "Animaciones de 200 a 400 ms y ausencia de blur pesado, overlays costosos o motion excesivo."
                )
                AboutPrinciple(
                    icon = Icons.Rounded.Eco,
                    title = "Sostenibilidad",
                    description = "Paleta verde neutra con acentos contenidos para exito, advertencia y error."
                )
            }
        }

        item {
            EcoSectionCard(
                title = "Paleta",
                subtitle = "Verdes modernos, neutros suaves y acentos minimos para estados criticos."
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    EcoColorSwatch(label = "Verde", color = EcoGreen)
                    EcoColorSwatch(label = "Verde claro", color = EcoGreenLight)
                    EcoColorSwatch(label = "Verde oscuro", color = EcoGreenDark)
                    EcoColorSwatch(label = "Advertencia", color = WarningAmber)
                    EcoColorSwatch(label = "Error", color = ErrorRed)
                }
            }
        }

        item {
            EcoSectionCard(
                title = "Rendimiento",
                subtitle = "La prioridad de esta app es fluidez, estabilidad y respuesta consistente."
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EcoChip(text = "Sin blur pesado", containerColor = EcoSurfaceAlt, contentColor = EcoTextMuted)
                    EcoChip(text = "Overlays ligeros", containerColor = EcoSurfaceAlt, contentColor = EcoTextMuted)
                    EcoChip(text = "Estados controlados", containerColor = EcoSurfaceAlt, contentColor = EcoTextMuted)
                    EcoChip(text = "Main thread protegido", containerColor = EcoSurfaceAlt, contentColor = EcoTextMuted)
                }
                androidx.compose.material3.Text(
                    text = "La deteccion mantiene overlays sobrios, el analisis sigue fuera del hilo principal y la interfaz usa composables simples para minimizar recomposiciones.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = EcoText
                )
            }
        }
    }
}

@Composable
private fun AboutPrinciple(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .background(EcoSurfaceAlt, CircleShape)
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = EcoGreen
            )
        }
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
