package com.ecomision.ecosort

import android.content.Context
import androidx.room.Room
import com.ecomision.ecosort.analysis.BinRuleEngine
import com.ecomision.ecosort.analysis.BoundingBoxIsolationEngine
import com.ecomision.ecosort.analysis.GuidedViewPlanner
import com.ecomision.ecosort.analysis.UncertaintyEngine
import com.ecomision.ecosort.analysis.WasteHeuristicClassifier
import com.ecomision.ecosort.camera.MlKitWasteObjectDetector
import com.ecomision.ecosort.data.db.EcoDatabase
import com.ecomision.ecosort.repository.CatalogRepository
import com.ecomision.ecosort.repository.CatalogRepositoryImpl
import com.ecomision.ecosort.repository.ScanHistoryRepository
import com.ecomision.ecosort.repository.ScanHistoryRepositoryImpl

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database: EcoDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            EcoDatabase::class.java,
            "ecosort.db"
        ).fallbackToDestructiveMigration().build()
    }

    val catalogRepository: CatalogRepository by lazy {
        CatalogRepositoryImpl(
            context = appContext,
            categoryDao = database.wasteCategoryDao(),
            ruleDao = database.binRuleDao()
        )
    }

    val scanHistoryRepository: ScanHistoryRepository by lazy {
        ScanHistoryRepositoryImpl(database.scanHistoryDao())
    }

    val wasteObjectDetector by lazy { MlKitWasteObjectDetector() }
    val isolationEngine by lazy { BoundingBoxIsolationEngine() }
    val classifier by lazy { WasteHeuristicClassifier(appContext) }
    val binRuleEngine by lazy { BinRuleEngine() }
    val uncertaintyEngine by lazy { UncertaintyEngine() }
    val guidedViewPlanner by lazy { GuidedViewPlanner() }
}
