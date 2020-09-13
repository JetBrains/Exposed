package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.statements.api.ExposedConnection
import org.jetbrains.exposed.sql.statements.api.ExposedDatabaseMetadata
import org.jetbrains.exposed.sql.transactions.*
import org.jetbrains.exposed.sql.vendors.*
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.sql.ConnectionPoolDataSource
import javax.sql.DataSource

class Database private constructor(private val resolvedVendor: String? = null, val connector: () -> ExposedConnection<*>) {

    var useNestedTransactions: Boolean = false

    internal fun <T> metadata(body: ExposedDatabaseMetadata.() -> T) : T {
        val transaction = TransactionManager.currentOrNull()
        return if (transaction == null) {
            val connection = connector()
            try {
                connection.metadata(body)
            } finally {
                connection.close()
            }
        } else
            transaction.connection.metadata(body)
    }

    val url: String by lazy { metadata { url } }
    val vendor: String by lazy {
        resolvedVendor ?: metadata { databaseDialectName }
    }

    val dialect by lazy {
        dialects[vendor.toLowerCase()]?.invoke() ?: error("No dialect registered for $name. URL=$url")
    }

    val version by lazy { metadata { version } }

    fun isVersionCovers(version: BigDecimal) = this.version >= version

    val supportsAlterTableWithAddColumn by lazy(LazyThreadSafetyMode.NONE) { metadata { supportsAlterTableWithAddColumn } }
    val supportsMultipleResultSets by lazy(LazyThreadSafetyMode.NONE) { metadata { supportsMultipleResultSets } }

    val identifierManager by lazy { metadata { identifierManager } }

    var defaultFetchSize: Int? = null
        private set

    fun defaultFetchSize(size: Int): Database {
        defaultFetchSize = size
        return this
    }

    companion object {
        private val dialects = ConcurrentHashMap<String, () -> DatabaseDialect>()

        private val connectionInstanceImpl : DatabaseConnectionAutoRegistration =
                ServiceLoader.load(DatabaseConnectionAutoRegistration::class.java, Database::class.java.classLoader).firstOrNull() ?: error("Can't load implementation for ${DatabaseConnectionAutoRegistration::class.simpleName}")

        init {
            registerDialect(H2Dialect.dialectName) { H2Dialect() }
            registerDialect(MysqlDialect.dialectName) { MysqlDialect() }
            registerDialect(PostgreSQLDialect.dialectName) { PostgreSQLDialect() }
            registerDialect(PostgreSQLNGDialect.dialectName) { PostgreSQLNGDialect() }
            registerDialect(SQLiteDialect.dialectName) { SQLiteDialect() }
            registerDialect(OracleDialect.dialectName) { OracleDialect() }
            registerDialect(SQLServerDialect.dialectName) { SQLServerDialect() }
            registerDialect(MariaDBDialect.dialectName) { MariaDBDialect() }
        }

        fun registerDialect(prefix:String, dialect: () -> DatabaseDialect) {
            dialects[prefix] = dialect
        }

        /**
         * Attempts to make a connection to a database.
         *
         * @param explicitVendor    the vendor name, if it's `null` then it will be resolved
         * from database dialect name
         * @param getNewConnection  used to create a new connection
         * @param schema            the default schema to use
         * @param setupConnection   used to apply operations on the established connection
         * @param manager           used to register a transaction manager
         */
        private fun doConnect(
            explicitVendor: String?,
            getNewConnection: () -> Connection,
            schema: Schema? = null,
            setupConnection: (Connection) -> Unit = {},
            manager: (Database) -> TransactionManager = { ThreadLocalTransactionManager(it, DEFAULT_REPETITION_ATTEMPTS) }
        ): Database {
            val database = Database(explicitVendor) {
                connectionInstanceImpl(getNewConnection().apply { setupConnection(this) })
            }.apply {
                TransactionManager.registerManager(this, manager(this))
            }

            if(schema != null) {
                with(database.transactionManager.newTransaction()) {
                    SchemaUtils.setSchema(schema)
                }
            }

            return database
        }

        /**
         * Attempts to make a connection to a database from a [DataSource]
         * for advanced behaviors such as connection pooling.
         *
         * @param dataSource        the data source to create the connection
         * @param schema            the default schema to use
         * @param setupConnection   used to apply operations on the [dataSource] connection
         * @param manager           used to register a transaction manager
         */
        fun connect(
            dataSource: DataSource,
            schema: Schema? = null,
            setupConnection: (Connection) -> Unit = {},
            manager: (Database) -> TransactionManager = {
                ThreadLocalTransactionManager(it, DEFAULT_REPETITION_ATTEMPTS)
            }
        ): Database {
            return doConnect(explicitVendor = null,
                             getNewConnection = { dataSource.connection!! },
                             schema = schema,
                             setupConnection = setupConnection,
                             manager = manager)
        }

        /**
         * Attempts to make a pooled connection to a database
         *
         * @param dataSource        the data source to create the connection
         * @param schema            the default schema to use
         * @param setupConnection   used to apply operations on the [dataSource] connection
         * @param manager           used to register a transaction manager
         */
        @Deprecated(level = DeprecationLevel.ERROR, replaceWith = ReplaceWith("connectPool(datasource, setupConnection, manager)"), message = "Use connectPool instead")
        fun connect(
            dataSource: ConnectionPoolDataSource,
            schema: Schema? = null,
            setupConnection: (Connection) -> Unit = {},
            manager: (Database) -> TransactionManager = {
                ThreadLocalTransactionManager(it, DEFAULT_REPETITION_ATTEMPTS)
            }
        ): Database {
            return doConnect(explicitVendor = null,
                             getNewConnection = { dataSource.pooledConnection.connection!! },
                             schema = schema,
                             setupConnection = setupConnection,
                             manager = manager)
        }

        /**
         * Attempts to make a pooled connection to a database
         *
         * @param dataSource        the data source to create the connection
         * @param schema            the default schema to use
         * @param setupConnection   used to apply operations on the [dataSource] connection
         * @param manager           used to register a transaction manager
         */
        fun connectPool(
            dataSource: ConnectionPoolDataSource,
            schema: Schema? = null,
            setupConnection: (Connection) -> Unit = {},
            manager: (Database) -> TransactionManager = {
                ThreadLocalTransactionManager(it, DEFAULT_REPETITION_ATTEMPTS)
            }
        ): Database {
            return doConnect(explicitVendor = null,
                             getNewConnection = { dataSource.pooledConnection.connection!! },
                             schema = schema,
                             setupConnection = setupConnection,
                             manager = manager)
        }

        /**
         * Attempts to make a connection to a database from a [java.sql.Connection] instance.
         *
         * @param getNewConnection  used to create a new JDBC connection
         * @param schema            the default schema to use
         * @param manager           used to register a transaction manager
         */
        fun connect(
            getNewConnection: () -> Connection,
            schema: Schema? = null,
            manager: (Database) -> TransactionManager = {
                ThreadLocalTransactionManager(it, DEFAULT_REPETITION_ATTEMPTS)
            }
        ): Database {
            return doConnect(explicitVendor = null,
                             getNewConnection = getNewConnection,
                             schema = schema,
                             manager = manager)
        }

        /**
         * Attempts to make a connection to a database.
         *
         * @param url       the database url
         * @param driver    the driver class
         * @param user      the database user who wants to make the connection
         * @param password  the user's password
         * @param schema    the default schema to use
         * @param setupConnection used to apply operations on the JDBC connection made
         * with the [url], [user] and [password]
         * @param manager   used to register a transaction manager
         */
        fun connect(
                url: String,
                driver: String = getDriver(url),
                user: String = "",
                password: String = "",
                schema: Schema? = null,
                setupConnection: (Connection) -> Unit = {},
                manager: (Database) -> TransactionManager = {
                    ThreadLocalTransactionManager(it, DEFAULT_REPETITION_ATTEMPTS)
                }
        ): Database {
            Class.forName(driver).newInstance()

            return doConnect(getDialectName(url),
                             { DriverManager.getConnection(url, user, password) },
                             schema,
                             setupConnection,
                             manager)
        }

        fun getDefaultIsolationLevel(db: Database) : Int =
            when(db.vendor) {
                SQLiteDialect.dialectName -> Connection.TRANSACTION_SERIALIZABLE
                OracleDialect.dialectName -> Connection.TRANSACTION_READ_COMMITTED
                else -> DEFAULT_ISOLATION_LEVEL
            }

        private fun getDriver(url: String) = when {
            url.startsWith("jdbc:h2") -> "org.h2.Driver"
            url.startsWith("jdbc:postgresql") -> "org.postgresql.Driver"
            url.startsWith("jdbc:pgsql") -> "com.impossibl.postgres.jdbc.PGDriver"
            url.startsWith("jdbc:mysql") -> "com.mysql.cj.jdbc.Driver"
            url.startsWith("jdbc:mariadb") -> "org.mariadb.jdbc.Driver"
            url.startsWith("jdbc:oracle") -> "oracle.jdbc.OracleDriver"
            url.startsWith("jdbc:sqlite") -> "org.sqlite.JDBC"
            url.startsWith("jdbc:sqlserver") -> "com.microsoft.sqlserver.jdbc.SQLServerDriver"
            else -> error("Database driver not found for $url")
        }

        private fun getDialectName(url: String) = when {
            url.startsWith("jdbc:h2") -> H2Dialect.dialectName
            url.startsWith("jdbc:postgresql") -> PostgreSQLDialect.dialectName
            url.startsWith("jdbc:pgsql") -> PostgreSQLNGDialect.dialectName
            url.startsWith("jdbc:mysql") -> MysqlDialect.dialectName
            url.startsWith("jdbc:mariadb") -> MariaDBDialect.dialectName
            url.startsWith("jdbc:oracle") -> OracleDialect.dialectName
            url.startsWith("jdbc:sqlite") -> SQLiteDialect.dialectName
            url.startsWith("jdbc:sqlserver") -> SQLServerDialect.dialectName
            else -> error("Can't resolve dialect for connection: $url")
        }
    }
}

interface DatabaseConnectionAutoRegistration : (Connection) -> ExposedConnection<*>

val Database.name : String get() = url.substringAfterLast('/').substringBefore('?')
