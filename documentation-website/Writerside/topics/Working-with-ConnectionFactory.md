# Working with ConnectionFactory

<primary-label ref="r2dbc"/>

In R2DBC, [`ConnectionFactory`](https://javadoc.io/doc/io.r2dbc/r2dbc-spi/latest/io/r2dbc/spi/ConnectionFactory.html)
from the `io.r2dbc.spi` package is the reactive equivalent of a [`DataSource`](Working-with-DataSource.md) in JDBC.
It is responsible for producing non-blocking `Connection` instances that support reactive and coroutine-based database
access.

In Exposed, the `exposed-r2dbc` module integrates R2DBC support by allowing you to connect to a database using a 
`ConnectionFactory`. This is done through the `R2dbcDatabase.connect()` function.

## Create a `ConnectionFactory`

To establish a connection to a database using R2DBC, you first need to create a `ConnectionFactory`. This is typically
done by using the
[`ConnectionFactoryOptions`](https://javadoc.io/doc/io.r2dbc/r2dbc-spi/latest/io/r2dbc/spi/ConnectionFactoryOptions.html)
builder and passing it to `ConnectionFactories.get()`:

```kotlin
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.ConnectionFactoryOptions.*

val connectionFactory = ConnectionFactories.get(
    ConnectionFactoryOptions.builder()
        .option(DRIVER, "postgresql")
        .option(HOST, "localhost")
        .option(PORT, 5432)
        .option(USER, "your_username")
        .option(PASSWORD, "your_password")
        .option(DATABASE, "your_database")
        .build()
)
```

This gives you a non-blocking `ConnectionFactory` compatible with Exposed's `R2dbcDatabase`.

## Connecting to a database 

To connect Exposed to a database using R2DBC, you can either provide an explicit `ConnectionFactory`, or define a 
connection via a URL and configuration options.

### Use a `ConnectionFactory`

Once you’ve created a `ConnectionFactory`, you can establish a connection to your database by passing it to the
`R2dbcDatabase.connect()` function:

```kotlin
val database = R2dbcDatabase.connect(connectionFactory = connectionFactory)
```

This registers the connection source so that you can perform coroutine-based database operations using 
[`suspendTransaction`](Transactions.md#suspend-based-transaction).

### Use a URL and `connectionFactoryOptions`

As an alternative to manually building a `ConnectionFactory`, you can connect to a database in a more concise way using
the `R2dbcDatabase.connect()` function with a URL and optional `connectionFactoryOptions`:

```kotlin
val database = R2dbcDatabase.connect(
    url = "r2dbc:postgresql://db:5432/test",
    databaseConfig = {
        connectionFactoryOptions {
            option(ConnectionFactoryOptions.USER, "user")
            option(ConnectionFactoryOptions.PASSWORD, "password")
        }
    }
)
```


