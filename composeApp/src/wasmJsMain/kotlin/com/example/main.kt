package com.example

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.example.data.SmokeRepository
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val repository = SmokeRepository()
    val container = document.getElementById("compose-app") ?: document.body!!
    ComposeViewport(container) {
        App(repository)
    }
}
