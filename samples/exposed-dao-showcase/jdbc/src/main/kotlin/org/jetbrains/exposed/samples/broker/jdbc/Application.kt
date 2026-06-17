@file:Suppress("InvalidPackageDeclaration")
package org.jetbrains.exposed.samples.broker.jdbc

import io.ktor.server.application.*
import org.jetbrains.exposed.samples.broker.jdbc.plugins.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureDatabase()
    configureRouting()
}
