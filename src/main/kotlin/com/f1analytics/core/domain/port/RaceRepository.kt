package com.f1analytics.core.domain.port

import com.f1analytics.core.domain.model.Race

interface RaceRepository {
    suspend fun findByYear(year: Int): List<Race>
    suspend fun findByKey(key: Int): Race?
    /** Returns the race whose dateStart is closest to today (past or future). */
    suspend fun findCurrent(): Race?
}
