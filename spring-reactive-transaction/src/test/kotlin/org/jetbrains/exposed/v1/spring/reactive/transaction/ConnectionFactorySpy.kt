package org.jetbrains.exposed.v1.spring.reactive.transaction

import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryMetadata
import kotlinx.coroutines.reactive.awaitFirst
import org.reactivestreams.Publisher

internal class ConnectionFactorySpy(
    private val connectionSpy: (Connection) -> Connection
) : ConnectionFactory {
    private val connectionPublisher = ConnectionFactories.get("r2dbc:h2:mem:///test").create()

    suspend fun getCon(): Connection = connectionSpy(connectionPublisher.awaitFirst())

    override fun create(): Publisher<out Connection?> = connectionPublisher

    override fun getMetadata(): ConnectionFactoryMetadata = throw NotImplementedError()
}
