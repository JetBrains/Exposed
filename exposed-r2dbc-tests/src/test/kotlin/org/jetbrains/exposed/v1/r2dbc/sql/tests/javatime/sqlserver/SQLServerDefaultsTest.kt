package org.jetbrains.exposed.v1.r2dbc.sql.tests.javatime.sqlserver

import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.javatime.JavaLocalDateTimeColumnType
import org.jetbrains.exposed.v1.javatime.datetime
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SQLServerDefaultsTest : R2dbcDatabaseTestsBase() {
    @Test
    fun testDefaultExpressionsForTemporalTable() {
        fun databaseGeneratedTimestamp() = object : ExpressionWithColumnType<LocalDateTime>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { +"DEFAULT" }
            override val columnType: IColumnType<LocalDateTime> = JavaLocalDateTimeColumnType()
        }

        val temporalTable = object : UUIDTable("TemporalTable") {
            val name = text("name")
            val sysStart = datetime("sysStart").defaultExpression(databaseGeneratedTimestamp())
            val sysEnd = datetime("sysEnd").defaultExpression(databaseGeneratedTimestamp())
        }

        withDb(TestDB.SQLSERVER) {
            try {
                exec(
                    """
                        CREATE TABLE TemporalTable
                        (
                            id       uniqueidentifier PRIMARY KEY,
                            "name"   VARCHAR(100) NOT NULL,
                            sysStart DATETIME2 GENERATED ALWAYS AS ROW START,
                            sysEnd   DATETIME2 GENERATED ALWAYS AS ROW END,
                            PERIOD FOR SYSTEM_TIME ([sysStart], [sysEnd])
                        )
                    """.trimIndent()
                )

                val names = listOf("name")
                val batchInsert: List<ResultRow> =
                    temporalTable.batchInsert(names, shouldReturnGeneratedValues = true) {
                        this[temporalTable.name] = "name"
                    }
                val id = batchInsert.first()[temporalTable.id]
                val result = temporalTable.selectAll().where { temporalTable.id eq id }.single()
                assertEquals("name", result[temporalTable.name])
                assertNotNull(result[temporalTable.sysStart])
                assertNotNull(result[temporalTable.sysEnd])
            } finally {
                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.drop(temporalTable)
            }
        }
    }
}
