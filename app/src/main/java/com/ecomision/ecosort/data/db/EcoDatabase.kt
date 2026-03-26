package com.ecomision.ecosort.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ecomision.ecosort.data.db.dao.BinRuleDao
import com.ecomision.ecosort.data.db.dao.ScanHistoryDao
import com.ecomision.ecosort.data.db.dao.WasteCategoryDao
import com.ecomision.ecosort.data.db.entity.BinRuleEntity
import com.ecomision.ecosort.data.db.entity.ScanHistoryEntity
import com.ecomision.ecosort.data.db.entity.WasteCategoryEntity

@Database(
    entities = [WasteCategoryEntity::class, BinRuleEntity::class, ScanHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class EcoDatabase : RoomDatabase() {
    abstract fun wasteCategoryDao(): WasteCategoryDao
    abstract fun binRuleDao(): BinRuleDao
    abstract fun scanHistoryDao(): ScanHistoryDao
}

