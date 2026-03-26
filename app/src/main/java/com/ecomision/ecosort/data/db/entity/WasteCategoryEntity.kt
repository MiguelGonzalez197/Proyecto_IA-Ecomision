package com.ecomision.ecosort.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "waste_categories")
data class WasteCategoryEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val family: String,
    val defaultBin: String,
    val requiredViewsJson: String,
    val guidanceTagsJson: String
)

