package org.jetbrains.exposed.sqlserver

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.junit.Test
import java.time.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SQLServerDefaultsTest : DatabaseTestsBase() {

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
                    temporalTable.batchInsert(names, shouldReturnGeneratedValues = true) { name ->
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
