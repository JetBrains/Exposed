package org.jetbrains.exposed.v1.r2dbc.sql.tests.kotlindatetime.sqlserver

import kotlinx.coroutines.flow.single
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.r2dbc.sql.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.sql.batchInsert
import org.jetbrains.exposed.v1.r2dbc.sql.selectAll
import org.jetbrains.exposed.v1.r2dbc.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.sql.tests.TestDB
import org.jetbrains.exposed.v1.sql.kotlin.datetime.KotlinLocalDateTimeColumnType
import org.jetbrains.exposed.v1.sql.kotlin.datetime.datetime
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SQLServerDefaultsTest : R2dbcDatabaseTestsBase() {
    @Test
    fun testDefaultExpressionsForTemporalTable() {
        fun databaseGeneratedTimestamp() = object : ExpressionWithColumnType<LocalDateTime>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { +"DEFAULT" }
            override val columnType: IColumnType<LocalDateTime> = KotlinLocalDateTimeColumnType()
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
                SchemaUtils.drop(temporalTable)
            }
        }
    }
}
