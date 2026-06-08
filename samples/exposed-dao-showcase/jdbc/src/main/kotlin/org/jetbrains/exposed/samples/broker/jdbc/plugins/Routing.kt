@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.jdbc.plugins

import io.ktor.server.application.*
import org.jetbrains.exposed.samples.broker.jdbc.routes.*

fun Application.configureRouting() {
    brokerRoutes()
    clientRoutes()
    instrumentRoutes()
    portfolioRoutes()
    tradeRoutes()
    seedRoutes()
}
