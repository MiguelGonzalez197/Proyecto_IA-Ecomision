package com.ecomision.ecosort.analysis

import com.ecomision.ecosort.model.EvidenceSlot
import com.ecomision.ecosort.model.GuidedViewInstruction
import com.ecomision.ecosort.model.ScanSession
import com.ecomision.ecosort.model.WasteCategory

class GuidedViewPlanner {

    fun plan(
        category: WasteCategory?,
        session: ScanSession,
        missingSlots: List<EvidenceSlot>
    ): GuidedViewInstruction {
        val latest = session.observations.lastOrNull()
        if (latest != null && latest.blurScore > 0.62f) {
            return GuidedViewInstruction(
                slot = EvidenceSlot.SEPARATED_BACKGROUND,
                title = "Acerca y estabiliza la toma",
                description = "Acerca la camara, centra el objeto y reduce el fondo para mejorar la evidencia."
            )
        }
        if (latest != null && latest.occlusionScore > 0.50f) {
            return GuidedViewInstruction(
                slot = EvidenceSlot.SEPARATED_BACKGROUND,
                title = "Retira la mano y reencuadra",
                description = "Separa la mano u otros objetos y deja el residuo completo dentro del cuadro."
            )
        }

        val slot = missingSlots.firstOrNull() ?: fallbackSlotFor(category)
        return instructionFor(category, slot)
    }

    private fun fallbackSlotFor(category: WasteCategory?): EvidenceSlot {
        return when (category?.id) {
            "organic_food" -> EvidenceSlot.CLOSE_TEXTURE
            "plastic_bag", "metalized_wrapper" -> EvidenceSlot.BOTH_FACES
            else -> EvidenceSlot.INNER_VIEW
        }
    }

    private fun instructionFor(
        category: WasteCategory?,
        slot: EvidenceSlot
    ): GuidedViewInstruction {
        val categoryName = category?.displayName ?: "objeto"
        return when (slot) {
            EvidenceSlot.INNER_VIEW -> when (category?.id) {
                "coffee_cup", "disposable_cup" -> GuidedViewInstruction(
                    slot = slot,
                    title = "Muestra el interior del vaso",
                    description = "Inclina el vaso y acerca la camara al borde para verificar espuma, cafe o residuos adheridos."
                )

                "plastic_bottle", "glass_jar", "tetra_pak" -> GuidedViewInstruction(
                    slot = slot,
                    title = "Muestra el interior del envase",
                    description = "Gira lentamente el envase y deja ver si quedan liquidos, gotas o residuos viscosos."
                )

                "delivery_container", "plastic_container", "foam_tray" -> GuidedViewInstruction(
                    slot = slot,
                    title = "Muestra la parte interna",
                    description = "Abre el recipiente y acerca la camara a esquinas y fondo para revisar salsa, grasa o comida."
                )

                "pizza_box" -> GuidedViewInstruction(
                    slot = slot,
                    title = "Muestra la parte interna de la caja",
                    description = "Abre la caja y enfoca zonas con queso, salsa o grasa."
                )

                else -> GuidedViewInstruction(
                    slot = slot,
                    title = "Muestra el interior",
                    description = "Acerca la camara al interior del $categoryName para buscar restos o liquidos."
                )
            }

            EvidenceSlot.OPENING_NECK -> when (category?.id) {
                "can" -> GuidedViewInstruction(
                    slot = slot,
                    title = "Muestra la abertura de la lata",
                    description = "Acerca la camara a la parte superior para comprobar si quedaron liquidos o residuos."
                )

                else -> GuidedViewInstruction(
                    slot = slot,
                    title = "Muestra la abertura",
                    description = "Enfoca la boca o tapa del $categoryName para validar si esta vacio."
                )
            }

            EvidenceSlot.BOTTOM_VIEW -> GuidedViewInstruction(
                slot = slot,
                title = "Muestra la base",
                description = "Gira el $categoryName y enfoca la base para revisar escurrimientos, grasa o restos pegados."
            )

            EvidenceSlot.BOTH_FACES -> when (category?.id) {
                "plastic_bag" -> GuidedViewInstruction(
                    slot = slot,
                    title = "Extiende la bolsa",
                    description = "Muestra ambas caras de la bolsa y separala del fondo para detectar residuos adheridos."
                )

                "napkin", "paper_sheet" -> GuidedViewInstruction(
                    slot = slot,
                    title = "Muestra ambas caras",
                    description = "Gira el material y ensena las dos caras para validar manchas, humedad o uso."
                )

                else -> GuidedViewInstruction(
                    slot = slot,
                    title = "Gira el objeto",
                    description = "Muestra ambas caras del $categoryName para completar la evidencia."
                )
            }

            EvidenceSlot.BACK_SIDE -> GuidedViewInstruction(
                slot = slot,
                title = "Muestra el otro lado",
                description = "Gira el $categoryName y enfoca esquinas o zonas ocultas donde puede haber grasa o humedad."
            )

            EvidenceSlot.CLOSE_TEXTURE -> when (category?.id) {
                "organic_food" -> GuidedViewInstruction(
                    slot = slot,
                    title = "Acerca la textura",
                    description = "Muestra la textura y el volumen completo del residuo organico, separado de otros objetos."
                )

                else -> GuidedViewInstruction(
                    slot = slot,
                    title = "Haz un acercamiento",
                    description = "Acerca la camara a las manchas o a la superficie para buscar restos o humedad."
                )
            }

            EvidenceSlot.UNFOLDED_VIEW -> GuidedViewInstruction(
                slot = slot,
                title = "Abre o extiende el empaque",
                description = "Si es posible, abre o extiende el $categoryName para revisar su cara interna."
            )

            EvidenceSlot.RIM_CLOSEUP -> GuidedViewInstruction(
                slot = slot,
                title = "Acerca la camara al borde",
                description = "Muestra el borde del vaso para confirmar espuma, cafe o azucar pegada."
            )

            EvidenceSlot.HINGE_VIEW -> GuidedViewInstruction(
                slot = slot,
                title = "Muestra bisagras y uniones",
                description = "Enfoca uniones y pliegues del recipiente porque suelen retener comida."
            )

            EvidenceSlot.SEPARATED_BACKGROUND -> GuidedViewInstruction(
                slot = slot,
                title = "Separa el objeto del fondo",
                description = "Reencuadra el residuo, retira la mano y evita sombras fuertes o elementos detras."
            )

            EvidenceSlot.OUTER_FULL -> GuidedViewInstruction(
                slot = slot,
                title = "Centra el objeto completo",
                description = "Muestra el $categoryName completo dentro del cuadro."
            )
        }
    }
}
