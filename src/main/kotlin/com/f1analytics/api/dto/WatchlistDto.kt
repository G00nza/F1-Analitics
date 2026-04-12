package com.f1analytics.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class WatchlistDto(
    val drivers: List<String>,
    val source: String      // "global" | "session"
)

@Serializable
data class WatchlistUpdateDto(val drivers: List<String>)
