package com.ecomision.ecosort.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ecomision.ecosort.data.db.entity.BinRuleEntity

@Dao
interface BinRuleDao {
    @Query("SELECT COUNT(*) FROM bin_rules")
    suspend fun count(): Int

    @Query("SELECT * FROM bin_rules ORDER BY priority DESC")
    suspend fun getAll(): List<BinRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<BinRuleEntity>)
}

