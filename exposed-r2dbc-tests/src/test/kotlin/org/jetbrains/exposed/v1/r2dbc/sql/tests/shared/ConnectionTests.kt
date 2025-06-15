package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import io.r2dbc.h2.H2ConnectionConfiguration
import io.r2dbc.h2.H2ConnectionFactory
import io.r2dbc.h2.H2ConnectionOption
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.Option
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.vendors.ColumnMetadata
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.Assume
import org.junit.Test
import java.sql.Types
import kotlin.test.assertNotNull

class ConnectionTests : R2dbcDatabaseTestsBase() {

    object People : LongIdTable() {
        val firstName = varchar("firstname", 80).nullable()
        val lastName = varchar("lastname", 42).default("Doe")
        val age = integer("age").default(18)
    }

    @Test
    fun testGettingColumnMetadata() {
        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_H2_V2, People) {
            val columnMetadata = connection.metadata {
                requireNotNull(columns(People)[People])
            }.toSet()

            val h2Dialect = (db.dialect as H2Dialect)
            val idType = "BIGINT"
            val firstNameType = if (h2Dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) "VARCHAR2(80)" else "VARCHAR(80)"
            val lastNameType = if (h2Dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) "VARCHAR2(42)" else "VARCHAR(42)"
            val ageType = if (h2Dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) "INTEGER" else "INT"

            val expected = setOf(
                ColumnMetadata(People.id.nameInDatabaseCase(), Types.BIGINT, idType, false, 64, null, h2Dialect.h2Mode != H2Dialect.H2CompatibilityMode.Oracle, null),
                ColumnMetadata(People.firstName.nameInDatabaseCase(), Types.VARCHAR, firstNameType, true, 80, null, false, null),
                ColumnMetadata(People.lastName.nameInDatabaseCase(), Types.VARCHAR, lastNameType, false, 42, null, false, "Doe"),
                ColumnMetadata(People.age.nameInDatabaseCase(), Types.INTEGER, ageType, false, 32, null, false, "18"),
            )

            assertEquals(expected, columnMetadata)
        }
    }

    @Test
    fun testTableConstraintsWithFKColumnsThatNeedQuoting() {
        val parent = object : LongIdTable("parent") {
            val scale = integer("scale").uniqueIndex()
        }
        val child = object : LongIdTable("child") {
            val scale = reference("scale", parent.scale)
        }

        withTables(child, parent) { testDb ->
            val constraints = connection.metadata {
                tableConstraints(listOf(child))
            }
            // tableConstraints() returns entries for all tables involved in the FK (parent + child)
            assertEquals(2, constraints.keys.size)

            // EXPOSED-711 https://youtrack.jetbrains.com/issue/EXPOSED-711/Oracle-tableConstraints-columnContraints-dont-return-foreign-keys
            // but only child entry has a non-empty list of FKs
            if (testDb != TestDB.ORACLE) {
                assertEquals(
                    1,
                    constraints.values.count { fks ->
                        fks.any { it.fkName == child.scale.foreignKey?.fkName }
                    }
                )
            }
        }
    }

    private val dbSingleParam by lazy {
        R2dbcDatabase.connect("r2dbc:h2:mem:///test;USER=exposed_user;DB_CLOSE_DELAY=-1;MODE=MySQL;")
    }

    private val dbMultipleParam by lazy {
        R2dbcDatabase.connect(
            url = "r2dbc:h2:mem:///test;DB_CLOSE_DELAY=-1;MODE=MySQL;",
            driver = "h2",
            user = "exposed_user"
        )
    }

    private val dbSetUrl by lazy {
        R2dbcDatabase.connect {
            setUrl("r2dbc:h2:mem:///test;USER=exposed_user;DB_CLOSE_DELAY=-1;MODE=MySQL;")
        }
    }

    private val dbSetUrlWithOptions by lazy {
        R2dbcDatabase.connect {
            setUrl("r2dbc:h2:mem:///test;DB_CLOSE_DELAY=-1;")
            connectionFactoryOptions {
                option(ConnectionFactoryOptions.USER, "exposed_user")
                option(Option.valueOf("MODE"), "MySQL")
            }
        }
    }

    private val dbOnlyOptions by lazy {
        R2dbcDatabase.connect {
            connectionFactoryOptions {
                option(ConnectionFactoryOptions.DRIVER, "h2")
                option(ConnectionFactoryOptions.PROTOCOL, "mem")
                option(ConnectionFactoryOptions.DATABASE, "test")
                option(ConnectionFactoryOptions.USER, "exposed_user")
                option(Option.valueOf("DB_CLOSE_DELAY"), "-1")
                option(Option.valueOf("MODE"), "MySQL")
            }
        }
    }

    private val dbConnectionFactory by lazy {
        val options = H2ConnectionConfiguration.builder()
            .inMemory("test")
            .username("exposed_user")
            .property(H2ConnectionOption.DB_CLOSE_DELAY, "-1")
            .property(H2ConnectionOption.MODE, "MySQL")
            .build()
        R2dbcDatabase.connect(
            connectionFactory = H2ConnectionFactory(options),
            databaseConfig = R2dbcDatabaseConfig {
                explicitDialect = H2Dialect()
            }
        )
    }

    @Test
    fun testConnectWithUrlStringParameter() = runTest {
        Assume.assumeTrue(TestDB.H2_V2_MYSQL in TestDB.enabledDialects())

        testConnectMethod(dbSingleParam)
    }

    @Test
    fun testConnectWithUrlStringAndOtherParameters() = runTest {
        Assume.assumeTrue(TestDB.H2_V2_MYSQL in TestDB.enabledDialects())

        testConnectMethod(dbMultipleParam)
    }

    @Test
    fun testConnectWithUrlStringConfiguration() = runTest {
        Assume.assumeTrue(TestDB.H2_V2_MYSQL in TestDB.enabledDialects())

        testConnectMethod(dbSetUrl)
    }

    @Test
    fun testConnectWithUrlStringAndOptionsConfiguration() = runTest {
        Assume.assumeTrue(TestDB.H2_V2_MYSQL in TestDB.enabledDialects())

        testConnectMethod(dbSetUrlWithOptions)
    }

    @Test
    fun testConnectWithOptionsConfiguration() = runTest {
        Assume.assumeTrue(TestDB.H2_V2_MYSQL in TestDB.enabledDialects())

        testConnectMethod(dbOnlyOptions)
    }

    @Test
    fun testConnectWithConnectionFactory() = runTest {
        Assume.assumeTrue(TestDB.H2_V2_MYSQL in TestDB.enabledDialects())

        testConnectMethod(dbConnectionFactory)
    }

    private suspend fun testConnectMethod(db: R2dbcDatabase) {
        suspendTransaction(db = db) {
            val user = exec("SELECT CURRENT_USER;") {
                it.get(0) as? String
            }?.singleOrNull()
            assertNotNull(user)
            assertEquals("EXPOSED_USER", user)

            val mode = exec("SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME = 'MODE';") {
                it.get(0) as? String
            }?.singleOrNull()
            assertNotNull(mode)
            assertEquals("MySQL", mode)
        }
    }
}
