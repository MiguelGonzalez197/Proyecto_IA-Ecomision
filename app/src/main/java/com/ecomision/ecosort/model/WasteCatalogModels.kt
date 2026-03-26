package com.ecomision.ecosort.model

data class WasteCategory(
    val id: String,
    val displayName: String,
    val family: WasteFamily,
    val defaultBin: BinType,
    val requiredViews: List<EvidenceSlot>,
    val guidanceTags: List<String>
)

data class BinRule(
    val id: String,
    val categoryId: String,
    val priority: Int,
    val conditions: String,
    val binType: BinType,
    val reason: String
)

