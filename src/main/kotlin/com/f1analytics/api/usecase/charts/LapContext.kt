package com.f1analytics.api.usecase.charts

import com.f1analytics.core.domain.model.DriverEntry
import com.f1analytics.core.domain.model.Lap
import com.f1analytics.core.domain.model.Stint

data class LapContext(
    val lap: Lap,
    val driver: DriverEntry,
    val stint: Stint,
    val gapToLeaderMs: Int?
)
