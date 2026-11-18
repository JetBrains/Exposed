package org.jetbrains.exposed.v1.tests.shared.ddl

import org.jetbrains.exposed.v1.core.AutoIncColumnType
import org.jetbrains.exposed.v1.core.ColumnWithTransform
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.shared.assertFalse
import org.jetbrains.exposed.v1.tests.shared.assertTrue
import org.junit.jupiter.api.Test

@JvmInline
value class TransformedUserId(val value: Long)

@JvmInline
value class TransformedSourceId(val value: Long)

class ColumnTransformDdlTests : DatabaseTestsBase() {

    private fun IColumnType<*>.containsAutoIncType(): Boolean = when (this) {
        is AutoIncColumnType -> true
        is ColumnWithTransform<*, *> -> delegate.containsAutoIncType()
        else -> false
    }

    // https://youtrack.jetbrains.com/issue/EXPOSED-896
    @Test
    fun testTransformedPrimaryKeyColumnIsNotNullInCreateDDL() {
        val userTable = object : Table("user_table") {
            val userId = long("user_id").transform(
                wrap = { TransformedUserId(it) },
                unwrap = { it.value }
            )
            override val primaryKey = PrimaryKey(userId)

            val inviterId = reference("inviter_id", userId).nullable()
        }

        withDb {
            val userColumnDdl = userTable.userId.descriptionDdl(false)
            val inviterColumnDdl = userTable.inviterId.descriptionDdl(false)

            // SQLite renders an explicit "NOT NULL" marker for non-autoinc PK columns, so only a standalone
            // "NULL" marker (after stripping "NOT NULL") proves the column was declared nullable.
            assertFalse(userColumnDdl.replace(" NOT NULL", "").contains("NULL"))
            assertTrue(inviterColumnDdl.contains("NULL"))

            assertFalse(userTable.userId.columnType.nullable)
            assertTrue(userTable.inviterId.columnType.nullable)

            val createTableStatement = SchemaUtils.createStatements(userTable)
                .first { it.startsWith("CREATE TABLE", ignoreCase = true) }
            assertFalse(createTableStatement.contains("user_id BIGINT NULL"))
        }
    }

    // https://youtrack.jetbrains.com/issue/EXPOSED-807
    @Test
    fun testReferenceToTransformedAutoIncColumnDoesNotInheritAutoIncrement() {
        val sourceTable = object : Table("source_table") {
            val id = long("id").autoIncrement().transform(
                wrap = { TransformedSourceId(it) },
                unwrap = { it.value }
            )
            override val primaryKey = PrimaryKey(id)
        }
        val referencingTable = object : Table("referencing_table") {
            val sourceId = reference("source_id", sourceTable.id)
        }

        withDb(excludeSettings = listOf(TestDB.SQLITE)) {
            SchemaUtils.create(sourceTable, referencingTable)

            assertTrue(sourceTable.id.columnType.containsAutoIncType())
            assertFalse(referencingTable.sourceId.columnType.containsAutoIncType())
            assertFalse(referencingTable.sourceId.columnType.nullable)
            assertFalse(referencingTable.sourceId.descriptionDdl(false).contains("AUTO_INCREMENT"))
        }
    }
}
