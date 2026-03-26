package com.ecomision.ecosort.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ecomision.ecosort.data.db.entity.ScanHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ScanHistoryEntity)

    @Query("SELECT * FROM scan_history ORDER BY createdAtEpochMs DESC LIMIT 25")
    fun observeRecent(): Flow<List<ScanHistoryEntity>>
}

