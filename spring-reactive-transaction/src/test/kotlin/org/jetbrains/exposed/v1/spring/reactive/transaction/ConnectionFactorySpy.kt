package org.jetbrains.exposed.v1.spring.reactive.transaction

import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryMetadata
import kotlinx.coroutines.reactive.awaitFirst
import org.reactivestreams.Publisher
import org.springframework.r2dbc.connection.ConnectionFactoryUtils
import reactor.core.publisher.Mono

class ConnectionFactorySpy(
    private val connectionSpy: (Connection) -> Connection
) : ConnectionFactory {
    private val connectionPublisher = ConnectionFactoryUtils.getConnection(
        ConnectionFactories.get("r2dbc:h2:mem:///test")
    )

    private lateinit var con: Connection

    suspend fun getCon(): Connection {
        if (::con.isInitialized.not()) {
            con = connectionSpy(connectionPublisher.awaitFirst())
        }
        return con
    }

    override fun create(): Publisher<out Connection?> = Mono.just(con)

    override fun getMetadata(): ConnectionFactoryMetadata = throw NotImplementedError()
}
