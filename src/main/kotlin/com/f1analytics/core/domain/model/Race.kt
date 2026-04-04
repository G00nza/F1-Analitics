package com.f1analytics.core.domain.model

data class Race(
    val key: Int,
    val name: String,
    val officialName: String?,
    val circuit: String,
    val country: String?,
    val year: Int,
    val round: Int?,
    val dateStart: String?,
    val dateEnd: String?
)
