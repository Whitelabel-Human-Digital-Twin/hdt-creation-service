package io.github.whdt.crf

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import org.slf4j.event.Level

fun Application.configureMonitoring() {
    install(CallLogging) { level = Level.INFO }   // logs method/path/status/duration per request
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception at ${call.request.local.uri}", cause)
            call.respondText("Internal Server Error: ${cause.message}", status = HttpStatusCode.InternalServerError)
        }
    }
}