package com.ecomision.ecosort.repository

import android.content.Context
import com.ecomision.ecosort.data.db.dao.BinRuleDao
import com.ecomision.ecosort.data.db.dao.WasteCategoryDao
import com.ecomision.ecosort.data.db.entity.BinRuleEntity
import com.ecomision.ecosort.data.db.entity.WasteCategoryEntity
import com.ecomision.ecosort.model.BinRule
import com.ecomision.ecosort.model.BinType
import com.ecomision.ecosort.model.EvidenceSlot
import com.ecomision.ecosort.model.WasteCategory
import com.ecomision.ecosort.model.WasteFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray

interface CatalogRepository {
    suspend fun seedIfNeeded()
    suspend fun getCategories(): List<WasteCategory>
    suspend fun getCategory(categoryId: String?): WasteCategory?
    suspend fun getRules(): List<BinRule>
}

class CatalogRepositoryImpl(
    private val context: Context,
    private val categoryDao: WasteCategoryDao,
    private val ruleDao: BinRuleDao
) : CatalogRepository {

    private val seedMutex = Mutex()

    override suspend fun seedIfNeeded() {
        seedMutex.withLock {
            if (categoryDao.count() > 0 && ruleDao.count() > 0) return

            val categories = parseCategories("waste_catalog.json")
            val rules = parseRules("bin_rules.json")
            categoryDao.insertAll(categories)
            ruleDao.insertAll(rules)
        }
    }

    override suspend fun getCategories(): List<WasteCategory> {
        seedIfNeeded()
        return categoryDao.getAll().map { it.toDomain() }
    }

    override suspend fun getCategory(categoryId: String?): WasteCategory? {
        if (categoryId == null) return null
        return getCategories().firstOrNull { it.id == categoryId }
    }

    override suspend fun getRules(): List<BinRule> {
        seedIfNeeded()
        return ruleDao.getAll().map { it.toDomain() }
    }

    private suspend fun parseCategories(assetName: String): List<WasteCategoryEntity> = withContext(Dispatchers.IO) {
        val json = context.assets.open(assetName).bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    WasteCategoryEntity(
                        id = item.getString("id"),
                        displayName = item.getString("displayName"),
                        family = item.getString("family"),
                        defaultBin = item.getString("defaultBin"),
                        requiredViewsJson = item.getJSONArray("requiresViews").toString(),
                        guidanceTagsJson = item.getJSONArray("guidanceTags").toString()
                    )
                )
            }
        }
    }

    private suspend fun parseRules(assetName: String): List<BinRuleEntity> = withContext(Dispatchers.IO) {
        val json = context.assets.open(assetName).bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    BinRuleEntity(
                        id = item.getString("id"),
                        categoryId = item.getString("categoryId"),
                        priority = item.getInt("priority"),
                        conditions = item.getString("conditions"),
                        binType = item.getString("binType"),
                        reason = item.getString("reason")
                    )
                )
            }
        }
    }
}

private fun WasteCategoryEntity.toDomain(): WasteCategory {
    return WasteCategory(
        id = id,
        displayName = displayName,
        family = WasteFamily.valueOf(family),
        defaultBin = BinType.valueOf(defaultBin),
        requiredViews = parseStringArray(requiredViewsJson).map(EvidenceSlot::valueOf),
        guidanceTags = parseStringArray(guidanceTagsJson)
    )
}

private fun BinRuleEntity.toDomain(): BinRule {
    return BinRule(
        id = id,
        categoryId = categoryId,
        priority = priority,
        conditions = conditions,
        binType = BinType.valueOf(binType),
        reason = reason
    )
}

private fun parseStringArray(rawJson: String): List<String> {
    val array = JSONArray(rawJson)
    return buildList {
        for (i in 0 until array.length()) {
            add(array.getString(i))
        }
    }
}

