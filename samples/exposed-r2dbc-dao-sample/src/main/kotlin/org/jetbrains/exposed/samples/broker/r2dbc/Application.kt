@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc

import io.ktor.server.application.*
import org.jetbrains.exposed.samples.broker.r2dbc.plugins.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

suspend fun Application.module() {
    configureSerialization()
    configureDatabase()
    configureRouting()
}
