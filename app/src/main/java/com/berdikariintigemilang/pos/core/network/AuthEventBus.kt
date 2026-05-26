package com.berdikariintigemilang.pos.core.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Menyiarkan event sesi-berakhir (401) agar UI bisa auto-logout ke Login. */
@Singleton
class AuthEventBus @Inject constructor() {
    private val _loggedOut = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loggedOut: SharedFlow<Unit> = _loggedOut.asSharedFlow()

    fun notifyLoggedOut() {
        _loggedOut.tryEmit(Unit)
    }
}
