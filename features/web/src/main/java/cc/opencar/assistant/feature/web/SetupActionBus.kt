package cc.opencar.assistant.feature.web

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Signals from Ktor setup APIs to the foreground Activity. */
object SetupActionBus {
    private val _runtimePermissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val runtimePermissionRequests: SharedFlow<Unit> = _runtimePermissionRequests.asSharedFlow()

    private val _openAndroidSettings = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openAndroidSettings: SharedFlow<Unit> = _openAndroidSettings.asSharedFlow()

    private val _overlayPermissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val overlayPermissionRequests: SharedFlow<Unit> = _overlayPermissionRequests.asSharedFlow()

    fun requestRuntimePermissions() {
        _runtimePermissionRequests.tryEmit(Unit)
    }

    fun requestOpenAndroidSettings() {
        _openAndroidSettings.tryEmit(Unit)
    }

    fun requestOverlayPermission() {
        _overlayPermissionRequests.tryEmit(Unit)
    }
}
