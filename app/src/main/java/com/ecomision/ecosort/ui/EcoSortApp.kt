package com.ecomision.ecosort.ui

import androidx.compose.runtime.Composable

@Composable
fun EcoSortApp(
    viewModel: ScannerViewModel
) {
    ScannerScreen(viewModel = viewModel)
}
