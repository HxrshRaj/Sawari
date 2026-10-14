package com.hxrsh.sawari.ui.common

import androidx.compose.runtime.Immutable

/** What a screen's main content is doing. Every screen renders all four. */
@Immutable
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data object Empty : UiState<Nothing>
    data class Error(val message: String) : UiState<Nothing>
    data class Content<T>(val data: T) : UiState<T>
}
