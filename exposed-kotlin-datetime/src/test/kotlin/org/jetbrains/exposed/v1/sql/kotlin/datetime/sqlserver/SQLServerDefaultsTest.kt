package org.jetbrains.exposed.v1.sql.kotlin.datetime.sqlserver

import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.dao.id.UUIDTable
import org.jetbrains.exposed.v1.sql.*
import org.jetbrains.exposed.v1.sql.kotlin.datetime.KotlinLocalDateTimeColumnType
import org.jetbrains.exposed.v1.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.v1.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.sql.tests.TestDB
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SQLServerDefaultsTest : DatabaseTestsBase() {

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
                val batchInsert: List<_root_ide_package_.org.jetbrains.exposed.v1.sql.ResultRow> =
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
