package org.jetbrains.exposed.dao.r2dbc.tests.shared

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.r2dbc.dao.entityCache
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.junit.jupiter.api.assertNotNull
import kotlin.test.Test

class AliasesTests : R2dbcDatabaseTestsBase() {
    @Test
    fun testWrapRowWithAliasedTable() {
        withTables(EntityTestsData.XTable, EntityTestsData.YTable) {
            val entity1 = EntityTestsData.XEntity.new {
                this.b1 = false
            }.flush()

            entityCache.clear()

            val alias = EntityTestsData.XTable.alias("xAlias")
            val entityFromAlias = alias.selectAll().map { EntityTestsData.XEntity.wrapRow(it, alias) }.singleOrNull()
            assertNotNull(entityFromAlias)
            assertEquals(entity1.id, entityFromAlias.id)
            assertEquals(false, entityFromAlias.b1)
        }
    }

    @Test
    fun testWrapRowWithAliasedQuery() {
        withTables(EntityTestsData.XTable, EntityTestsData.YTable) {
            val entity1 = EntityTestsData.XEntity.new {
                this.b1 = false
            }.flush()

            entityCache.clear()

            val alias = EntityTestsData.XTable.selectAll().alias("xAlias")
            val entityFromAlias = alias.selectAll().map { EntityTestsData.XEntity.wrapRow(it, alias) }.singleOrNull()
            assertNotNull(entityFromAlias)
            assertEquals(entity1.id, entityFromAlias.id)
            assertEquals(false, entityFromAlias.b1)
        }
    }
}
