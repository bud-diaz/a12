package com.paperweight.os.ui.components

// Uniform loading/error/content wrapper used by every dashboard screen's
// ViewModel — no retry queue, no cache fallback (per CLAUDE.md's network
// handling constraint), just a clear state to render inline.
sealed interface ScreenState<out T> {
    data object Loading : ScreenState<Nothing>
    data class Content<T>(val data: T) : ScreenState<T>
    data class Error(val message: String) : ScreenState<Nothing>
}
