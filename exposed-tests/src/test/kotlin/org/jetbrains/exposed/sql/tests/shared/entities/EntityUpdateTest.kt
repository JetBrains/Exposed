package org.jetbrains.exposed.sql.tests.shared.entities

import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import java.sql.SQLException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EntityUpdateTest : DatabaseTestsBase() {

    class CheckConstraint(id: EntityID<Int>) : IntEntity(id) {
        object Table : IntIdTable("check_constraint") {
            val number = integer("number").check { it greaterEq 1 }
        }

        var number by Table.number

        companion object : EntityClass<Int, CheckConstraint>(Table)
    }

    /**
     Issue: https://github.com/JetBrains/Exposed/issues/922
     */
    @Test
    fun testUpdateThrowsExceptionIfCheckConstraintFails() {
        withDatabaseInstance(TestDB.H2) {
            transaction {
                SchemaUtils.create(CheckConstraint.Table)
                CheckConstraint.new { number = 5 }
            }

            val entity = transaction { CheckConstraint.all().first() }
            assertEquals(5, entity.number)

            assertFailsWith<SQLException> {
                transaction {
                    maxAttempts = 1
                    addLogger(StdOutSqlLogger)
                    entity.number = -1
                }
            }

            // TODO What should we see here (-1 or 5)?
            assertEquals(5, entity.number)

            transaction {
                addLogger(StdOutSqlLogger)
                CheckConstraint.all().first()
            }.let {
                assertEquals(5, it.number)
            }
        }
    }
}
