# Working with ConnectionFactory

<primary-label ref="r2dbc"/>

In R2DBC, [`ConnectionFactory`](https://javadoc.io/doc/io.r2dbc/r2dbc-spi/latest/io/r2dbc/spi/ConnectionFactory.html)
from the `io.r2dbc.spi` package is the reactive equivalent of a [`DataSource`](Working-with-DataSource.md) in JDBC.
It is responsible for producing non-blocking `Connection` instances that support reactive and coroutine-based database
access.

In Exposed, the `exposed-r2dbc` module integrates R2DBC support by allowing you to connect to a database using a 
`ConnectionFactory`. This is done implicitly through the `R2dbcDatabase.connect()` function whenever a URL is passed:

```kotlin
```
{src="exposed-databases-r2dbc/src/main/kotlin/org/example/R2DBCDatabases.kt" include-symbol="database" }

This is equivalent to using the `R2dbcDatabase.connect()` overload that accepts a configuration block only:

```kotlin
import io.r2dbc.spi.IsolationLevel
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase

val database = R2dbcDatabase.connect {
    defaultMaxAttempts = 1
    defaultR2dbcIsolationLevel = IsolationLevel.READ_COMMITTED

    setUrl("r2dbc:h2:mem:///test;DB_CLOSE_DELAY=-1;")
}
```

## Using `ConnectionFactoryOptions`

When a URL is passed to `R2dbcDatabase.connect()`, the string is parsed to construct a new
[`ConnectionFactoryOptions`](https://r2dbc.io/spec/0.8.1.RELEASE/api/io/r2dbc/spi/ConnectionFactoryOptions.html) object,
which holds details of the configuration state related to the `ConnectionFactory`.

This state can be configured manually, using the `R2dbcDatabaseConfig.connectionFactoryOptions` builder, either alongside a provided URL:

```kotlin
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.IsolationLevel
import io.r2dbc.spi.Option
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase

val database = R2dbcDatabase.connect(
    url = "r2dbc:h2:mem:///test;",
    databaseConfig = {
        defaultMaxAttempts = 1
        defaultR2dbcIsolationLevel = IsolationLevel.READ_COMMITTED

        connectionFactoryOptions {
            option(Option.valueOf("DB_CLOSE_DELAY"), "-1")
        }
    }
)
```

Or by constructing the state holder fully from scratch:

```kotlin
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.IsolationLevel
import io.r2dbc.spi.Option
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase

val database = R2dbcDatabase.connect {
    defaultMaxAttempts = 1
    defaultR2dbcIsolationLevel = IsolationLevel.READ_COMMITTED

    connectionFactoryOptions {
        option(ConnectionFactoryOptions.DRIVER, "h2")
        option(ConnectionFactoryOptions.PROTOCOL, "mem")
        option(ConnectionFactoryOptions.DATABASE, "test")
        option(Option.valueOf("DB_CLOSE_DELAY"), "-1")
    }
}
```

You can also pre-construct a `ConnectionFactoryOptions` object and use it to initialize a custom `R2dbcDatabaseConfig`
instance. You can then pass both directly to `R2dbcDatabase.connect()` at a later point:

```kotlin
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.IsolationLevel
import io.r2dbc.spi.Option
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig

val options = ConnectionFactoryOptions.builder()
    .option(ConnectionFactoryOptions.DRIVER, "h2")
    .option(ConnectionFactoryOptions.PROTOCOL, "mem")
    .option(ConnectionFactoryOptions.DATABASE, "test")
    .option(Option.valueOf("DB_CLOSE_DELAY"), "-1")
    .build()

val databaseConfig = R2dbcDatabaseConfig {
    defaultMaxAttempts = 1
    defaultR2dbcIsolationLevel = IsolationLevel.READ_COMMITTED
    connectionFactoryOptions = options
}

val database = R2dbcDatabase.connect(databaseConfig = databaseConfig)
```

## Using a `ConnectionFactory`

To connect Exposed to a database using R2DBC, you can optionally rely on manual programmatic connection factory discovery
by providing an explicit `ConnectionFactory`. This connection source can then be passed to the `R2dbcDatabase.connect()` function:

```kotlin
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.IsolationLevel
import io.r2dbc.spi.Option
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig

val options = ConnectionFactoryOptions.builder()
    .option(ConnectionFactoryOptions.DRIVER, "h2")
    .option(ConnectionFactoryOptions.PROTOCOL, "mem")
    .option(ConnectionFactoryOptions.DATABASE, "test")
    .option(Option.valueOf("DB_CLOSE_DELAY"), "-1")
    .build()

val connectionFactory = ConnectionFactories.get(options)

val database = R2dbcDatabase.connect(
    connectionFactory = connectionFactory,
    databaseConfig = R2dbcDatabaseConfig {
        defaultMaxAttempts = 1
        defaultR2dbcIsolationLevel = IsolationLevel.READ_COMMITTED
        explicitDialect = H2Dialect()
    }
)
```

<note>
If an explicit <code>ConnectionFactory</code> is being used, it is required in this case that a value is set to
<code>R2dbcDatabaseConfig.explicitDialect</code>. This avoids any potential that the database dialect cannot be resolved
from the <code>ConnectionFactory</code> or its configuration options.
</note>

For simplicity or for more fine-tuned connection customization, you can use database-specific connection factories and
configuration builders via programmatic configuration. These database-specific objects can be
created and passed in the same way as the general R2DBC SPI objects:

```kotlin
import io.r2dbc.h2.H2ConnectionConfiguration
import io.r2dbc.h2.H2ConnectionFactory
import io.r2dbc.h2.H2ConnectionOption
import io.r2dbc.spi.IsolationLevel
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig

val connectionFactory = H2ConnectionFactory(
    H2ConnectionConfiguration.builder()
        .inMemory("test")
        .property(H2ConnectionOption.DB_CLOSE_DELAY, "-1")
        .build()
)

val database = R2dbcDatabase.connect(
    connectionFactory = connectionFactory,
    databaseConfig = R2dbcDatabaseConfig {
        defaultMaxAttempts = 1
        defaultR2dbcIsolationLevel = IsolationLevel.READ_COMMITTED
        explicitDialect = H2Dialect()
    }
)
```

Both these ways register the connection source so that you can perform coroutine-based database operations using
[`suspendTransaction`](Transactions.md#suspend-based-transaction).
