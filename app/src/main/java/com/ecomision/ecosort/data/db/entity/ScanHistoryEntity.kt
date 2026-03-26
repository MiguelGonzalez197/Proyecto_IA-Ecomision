package com.ecomision.ecosort.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: String?,
    val binType: String,
    val confidence: Float,
    val reason: String,
    val evidenceSummary: String,
    val createdAtEpochMs: Long
)

