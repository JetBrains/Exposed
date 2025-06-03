@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.r2dbc.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*
import org.slf4j.event.*

fun Application.configureMonitoring() {
    install(CallLogging) {
        level = Level.INFO
        filter { call ->
            call.request.path().startsWith("/")
        }
    }
}
