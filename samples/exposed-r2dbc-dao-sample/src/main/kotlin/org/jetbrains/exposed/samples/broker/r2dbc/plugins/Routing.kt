@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc.plugins

import io.ktor.server.application.*
import org.jetbrains.exposed.samples.broker.r2dbc.routes.*

fun Application.configureRouting() {
    brokerRoutes()
    clientRoutes()
    instrumentRoutes()
    portfolioRoutes()
    tradeRoutes()
    seedRoutes()
}
