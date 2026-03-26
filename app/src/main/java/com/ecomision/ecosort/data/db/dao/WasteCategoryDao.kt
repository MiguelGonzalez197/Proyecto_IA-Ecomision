package com.ecomision.ecosort.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ecomision.ecosort.data.db.entity.WasteCategoryEntity

@Dao
interface WasteCategoryDao {
    @Query("SELECT COUNT(*) FROM waste_categories")
    suspend fun count(): Int

    @Query("SELECT * FROM waste_categories")
    suspend fun getAll(): List<WasteCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<WasteCategoryEntity>)
}

