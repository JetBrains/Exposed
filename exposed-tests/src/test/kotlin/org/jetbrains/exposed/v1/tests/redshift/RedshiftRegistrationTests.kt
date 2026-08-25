package org.jetbrains.exposed.v1.tests.redshift

import org.jetbrains.exposed.v1.core.vendors.RedshiftDialect
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.statements.jdbc.JdbcDatabaseMetadataImpl
import org.jetbrains.exposed.v1.jdbc.vendors.RedshiftDialectMetadata
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.sql.DatabaseMetaData
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RedshiftRegistrationTests {
    @BeforeEach
    fun requireH2() {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
    }

    @Test
    fun testRedshiftUrlResolvesDialect() {
        val url = "jdbc:redshift://example.us-east-1.redshift.amazonaws.com:5439/dev"
        assertEquals(
            RedshiftDialect.dialectName,
            Database.getDialectName(url)
        )
        assertEquals(
            RedshiftDialect.dialectName,
            Database.getDialectName("jdbc:redshift:iam://example.us-east-1.redshift.amazonaws.com:5439/dev")
        )

        val getDriver = Database.Companion::class.java.getDeclaredMethod("getDriver", String::class.java)
        getDriver.isAccessible = true
        assertEquals("com.amazon.redshift.Driver", getDriver.invoke(Database.Companion, url))
    }

    @Test
    fun testRedshiftDriverMetadataResolvesDialect() {
        val metadata = Proxy.newProxyInstance(
            DatabaseMetaData::class.java.classLoader,
            arrayOf(DatabaseMetaData::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getDriverName" -> "Redshift JDBC Driver"
                else -> error("Unexpected DatabaseMetaData call: ${method.name}")
            }
        } as DatabaseMetaData

        assertEquals(
            RedshiftDialect.dialectName,
            JdbcDatabaseMetadataImpl("dev", metadata).databaseDialectName
        )
    }

    @Test
    fun testRedshiftMetadataRejectsDmlLimits() {
        assertFalse(RedshiftDialectMetadata().supportsLimitWithUpdateOrDelete())
    }
}
