package org.jetbrains.exposed.v1.spring7.reactive.transaction

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.r2dbc.ExperimentalR2dbcDaoApi
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.test.fail

@OptIn(ExperimentalR2dbcDaoApi::class)
open class EntityUpdateTest : SpringReactiveTransactionTestBase() {
    object T1 : IntIdTable() {
        val c1 = varchar("c1", Int.MIN_VALUE.toString().length)
    }

    class DAO(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<DAO>(T1)

        var c1 by T1.c1
    }

    @Test
//    @Transactional // see [runTestWithMockTransactional]
//    @Commit // see [runTestWithMockTransactional]
    open fun test1() = runTestWithMockTransactional(doCommit = true) {
        SchemaUtils.create(T1)
        T1.insert {
            it[c1] = "new"
        }
        Assertions.assertEquals("new", DAO.findById(1)?.c1)
    }

    @Test
//    @Transactional // see [runTestWithMockTransactional]
//    @Commit  // see [runTestWithMockTransactional]
    open fun test2() = runTestWithMockTransactional(doCommit = true) {
        val entity = DAO.findById(1) ?: fail()
        entity.c1 = "updated"
        Assertions.assertEquals("updated", DAO.findById(1)?.c1)
    }

    @Test
//    @Transactional // see [runTestWithMockTransactional]
//    @Commit  // see [runTestWithMockTransactional]
    open fun test3() = runTestWithMockTransactional(doCommit = true) {
        val entity = DAO.findById(1) ?: fail()
        Assertions.assertEquals("updated", entity.c1)
        SchemaUtils.drop(T1)
    }
}
