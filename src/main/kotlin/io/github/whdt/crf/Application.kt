package io.github.whdt.crf

import io.github.ktwinx.distributed.serde.Stub
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Stub.hdtJson)
    }

    install(CORS) {
        allowHost("localhost:3000")

        allowMethod(HttpMethod.Options) // 👈 Required for preflight
        allowMethod(HttpMethod.Post)    // 👈 Allow POST
        allowHeader(HttpHeaders.ContentType) // 👈 Allow content-type header
        allowHeader(HttpHeaders.Authorization) // (Optional)

    }
    configureMonitoring()
    configureRouting()
}
