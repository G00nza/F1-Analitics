package com.f1analytics.core.domain.port

interface SettingsRepository {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String)
}
