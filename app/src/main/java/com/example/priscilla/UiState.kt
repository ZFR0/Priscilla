package com.example.priscilla

/**
 * A generic sealed class that represents the state of a UI that loads data.
 * @param T The type of the data being loaded.
 */
sealed class UiState<out T> {
    /**
     * Represents the initial loading state before data is available.
     */
    data object Loading : UiState<Nothing>()

    /**
     * Represents the state where data has been successfully loaded.
     * @property data The loaded data.
     */
    data class Success<T>(val data: T) : UiState<T>()

    /**
     * Represents a state where an error occurred during loading.
     * @property message A descriptive error message.
     */
    data class Error(val message: String) : UiState<Nothing>()
}