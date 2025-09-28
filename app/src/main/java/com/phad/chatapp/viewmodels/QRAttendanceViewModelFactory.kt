package com.phad.chatapp.viewmodels

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Factory for creating QRAttendanceViewModel instances
 * Follows the same pattern as other ViewModelFactories in the app
 */
class QRAttendanceViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(QRAttendanceViewModel::class.java)) {
            return QRAttendanceViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
