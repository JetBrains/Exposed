@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.r2dbc

import io.ktor.server.application.*
import org.jetbrains.exposed.samples.r2dbc.plugins.configureDatabase
import org.jetbrains.exposed.samples.r2dbc.plugins.configureMonitoring
import org.jetbrains.exposed.samples.r2dbc.plugins.configureRouting
import org.jetbrains.exposed.samples.r2dbc.plugins.configureSerialization

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureMonitoring()
    configureSerialization()
    configureDatabase()
    configureRouting()
}
