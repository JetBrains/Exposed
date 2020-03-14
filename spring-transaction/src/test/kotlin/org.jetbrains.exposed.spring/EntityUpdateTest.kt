package org.jetbrains.exposed.spring

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.junit.Assert
import org.junit.Test
import org.springframework.test.annotation.Commit
import org.springframework.transaction.annotation.Transactional
import kotlin.test.fail

open class EntityUpdateTest : SpringTransactionTestBase() {

    object t1 : IntIdTable() {
        val c1 = varchar("c1", Int.MIN_VALUE.toString().length)
    }

    class dao(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<dao>(t1)
        var c1 by t1.c1
    }

    @Test @Transactional @Commit
    open fun test1() {
        SchemaUtils.create(t1)
        t1.insert {
            it[t1.c1] = "new"
        }
        Assert.assertEquals("new", dao.findById(1)?.c1)
    }

    @Test @Transactional @Commit
    open fun test2() {
        val entity = dao.findById(1) ?: fail()
        entity.c1 = "updated"
        Assert.assertEquals("updated", dao.findById(1)?.c1)
    }

    @Test @Transactional @Commit
    open fun test3() {
        val entity = dao.findById(1) ?: fail()
        Assert.assertEquals("updated", entity.c1)
        SchemaUtils.drop(t1)
    }
}