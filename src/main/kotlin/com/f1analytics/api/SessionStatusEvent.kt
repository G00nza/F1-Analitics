package com.f1analytics.api

sealed class SessionStatusEvent {
    abstract val eventName: String

    data class StartingSoon(
        val sessionName: String,
        val sessionKey: Int,
        val startsInSeconds: Double
    ) : SessionStatusEvent() {
        override val eventName = "session_starting_soon"
    }
}
