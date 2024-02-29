package ru.reosfire.money.manage

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*

fun main() {
    val server = embeddedServer(
        factory = Netty,
        port = 80,
        host = "0.0.0.0"
    ) {
        setupVue()
    }

    server.start(wait = true)
}

fun Application.setupVue() {
    routing {
        singlePageApplication {
            useResources = true
            defaultPage = "index.html"
            vue("web-app")
        }
    }
}
