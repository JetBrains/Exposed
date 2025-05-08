package org.jetbrains.exposed.v1.spring

import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.id.IntIdTable
import org.jetbrains.exposed.v1.sql.SchemaUtils
import org.jetbrains.exposed.v1.sql.insert
import org.junit.Assert
import org.junit.Test
import org.springframework.test.annotation.Commit
import org.springframework.transaction.annotation.Transactional
import kotlin.test.fail

open class EntityUpdateTest : SpringTransactionTestBase() {

    object T1 : IntIdTable() {
        val c1 = varchar("c1", Int.MIN_VALUE.toString().length)
    }

    class DAO(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<DAO>(T1)

        var c1 by T1.c1
    }

    @Test
    @Transactional
    @Commit
    open fun test1() {
        SchemaUtils.create(T1)
        T1.insert {
            it[c1] = "new"
        }
        Assert.assertEquals("new", DAO.findById(1)?.c1)
    }

    @Test
    @Transactional
    @Commit
    open fun test2() {
        val entity = DAO.findById(1) ?: fail()
        entity.c1 = "updated"
        Assert.assertEquals("updated", DAO.findById(1)?.c1)
    }

    @Test
    @Transactional
    @Commit
    open fun test3() {
        val entity = DAO.findById(1) ?: fail()
        Assert.assertEquals("updated", entity.c1)
        SchemaUtils.drop(T1)
    }
}
