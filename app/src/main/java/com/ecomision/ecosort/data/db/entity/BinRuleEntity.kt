package com.ecomision.ecosort.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bin_rules")
data class BinRuleEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val priority: Int,
    val conditions: String,
    val binType: String,
    val reason: String
)

