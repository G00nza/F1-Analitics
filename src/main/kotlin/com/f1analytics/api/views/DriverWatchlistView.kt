package com.f1analytics.api.views

import com.f1analytics.api.dto.WatchlistDto
import com.f1analytics.api.dto.WatchlistUpdateDto
import com.f1analytics.core.domain.port.SettingsRepository
import com.f1analytics.core.service.UndercutOvercutDetectionService.Companion.GLOBAL_WATCHLIST_KEY
import com.f1analytics.core.service.UndercutOvercutDetectionService.Companion.encodeDriverList
import com.f1analytics.core.service.UndercutOvercutDetectionService.Companion.parseDriverList
import com.f1analytics.core.service.UndercutOvercutDetectionService.Companion.sessionWatchlistKey
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond

class DriverWatchlistView(private val settingsRepository: SettingsRepository) {

    suspend fun handleGetGlobal(call: ApplicationCall) {
        val drivers = parseDriverList(settingsRepository.get(GLOBAL_WATCHLIST_KEY))
        call.respond(WatchlistDto(drivers = drivers.toList(), source = "global"))
    }

    suspend fun handleSetGlobal(call: ApplicationCall) {
        val body = call.receive<WatchlistUpdateDto>()
        settingsRepository.set(GLOBAL_WATCHLIST_KEY, encodeDriverList(body.drivers))
        call.respond(WatchlistDto(drivers = body.drivers, source = "global"))
    }

    suspend fun handleGetSession(call: ApplicationCall) {
        val sessionKey = call.parameters["sessionKey"]?.toIntOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, "Invalid session key")

        val sessionRaw = settingsRepository.get(sessionWatchlistKey(sessionKey))
        val (drivers, source) = if (sessionRaw != null) {
            parseDriverList(sessionRaw).toList() to "session"
        } else {
            parseDriverList(settingsRepository.get(GLOBAL_WATCHLIST_KEY)).toList() to "global"
        }
        call.respond(WatchlistDto(drivers = drivers, source = source))
    }

    suspend fun handleSetSession(call: ApplicationCall) {
        val sessionKey = call.parameters["sessionKey"]?.toIntOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, "Invalid session key")

        val body = call.receive<WatchlistUpdateDto>()
        settingsRepository.set(sessionWatchlistKey(sessionKey), encodeDriverList(body.drivers))
        call.respond(WatchlistDto(drivers = body.drivers, source = "session"))
    }
}
