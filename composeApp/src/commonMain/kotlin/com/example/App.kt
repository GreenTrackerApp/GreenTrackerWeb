package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import com.example.data.SmokeRepository
import com.example.ui.screens.SmokeTrackerScreen
import com.example.ui.theme.getColorsForTheme
import com.example.ui.viewmodel.SmokeViewModel

@Composable
fun App(repository: SmokeRepository) {
    val viewModel = remember { SmokeViewModel(repository) }
    val activeTheme by viewModel.activeTheme.collectAsState()

    MaterialTheme(
        colorScheme = getColorsForTheme(activeTheme)
    ) {
        SmokeTrackerScreen(viewModel)
    }
}
