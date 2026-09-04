package com.cxcboss.glassorb.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface OverlayRuntimeStatus {
    data object Stopped : OverlayRuntimeStatus
    data object Visible : OverlayRuntimeStatus
    data object Hidden : OverlayRuntimeStatus
    data object PermissionRequired : OverlayRuntimeStatus
    data class Error(val message: String) : OverlayRuntimeStatus
}

object OverlayRuntime {
    private val mutableStatus = MutableStateFlow<OverlayRuntimeStatus>(OverlayRuntimeStatus.Stopped)
    val status: StateFlow<OverlayRuntimeStatus> = mutableStatus.asStateFlow()

    fun update(status: OverlayRuntimeStatus) {
        mutableStatus.value = status
    }
}
