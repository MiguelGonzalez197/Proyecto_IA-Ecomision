package com.ecomision.ecosort

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.ecomision.ecosort.ui.EcoSortApp
import com.ecomision.ecosort.ui.ScannerViewModel
import com.ecomision.ecosort.ui.ScannerViewModelFactory
import com.ecomision.ecosort.ui.theme.EcoSortTheme

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<ScannerViewModel> {
        ScannerViewModelFactory(
            (application as EcoSortApplication).appContainer
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EcoSortTheme {
                EcoSortApp(viewModel = viewModel)
            }
        }
    }
}
