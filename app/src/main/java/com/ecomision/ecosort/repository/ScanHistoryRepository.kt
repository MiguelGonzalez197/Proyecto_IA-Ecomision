package com.ecomision.ecosort.repository

import com.ecomision.ecosort.data.db.dao.ScanHistoryDao
import com.ecomision.ecosort.data.db.entity.ScanHistoryEntity
import com.ecomision.ecosort.model.WasteAnalysis
import kotlinx.coroutines.flow.Flow

interface ScanHistoryRepository {
    suspend fun save(analysis: WasteAnalysis)
    fun observeRecent(): Flow<List<ScanHistoryEntity>>
}

class ScanHistoryRepositoryImpl(
    private val dao: ScanHistoryDao
) : ScanHistoryRepository {
    override suspend fun save(analysis: WasteAnalysis) {
        dao.insert(
            ScanHistoryEntity(
                categoryId = analysis.category?.id,
                binType = analysis.probableBin.name,
                confidence = analysis.confidence,
                reason = analysis.reason,
                evidenceSummary = analysis.evidenceUsed.joinToString(" | "),
                createdAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    override fun observeRecent(): Flow<List<ScanHistoryEntity>> = dao.observeRecent()
}
