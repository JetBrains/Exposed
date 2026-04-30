package org.jetbrains.exposed.dao.r2dbc.tests.shared

import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.flushCache
import org.jetbrains.exposed.r2dbc.dao.relationships.referencedOnSuspend
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import kotlin.test.Test

class R2dbcWarmUpLinkedReferencesTests : R2dbcDatabaseTestsBase() {

    object Box : IntIdTable() {
        val value = integer("value")
    }

    class EBox(id: EntityID<Int>) : IntR2dbcEntity(id) {
        var value by Box.value

        companion object : IntR2dbcEntityClass<EBox>(Box)
    }

    object BoxItem : IntIdTable() {
        val box = reference("box", Box)

        val value = integer("value")
    }

    class EBoxItem(id: EntityID<Int>) : IntR2dbcEntity(id) {
        var value by BoxItem.value
        val box by EBox referencedOnSuspend BoxItem.box

        companion object : IntR2dbcEntityClass<EBoxItem>(BoxItem)
    }

    @Test
    fun warmUpLinkedReferencesShouldNotReturnAllTheValueFromCache() {
        withTables(Box, BoxItem) {
            val boxEntities = (0..4).map {
                EBox.new {
                    value = it
                }
            }

            // TODO R2DBC: flush before reading `it.id.value` — `R2dbcDaoEntityID` cannot trigger a
            //  suspending `flushInserts` from a non-suspend property accessor, so we flush
            //  explicitly to populate the auto-increment ids before referencing them below.
            flushCache()

            boxEntities.forEach {
                val e = EBoxItem.new {
                    value = it.id.value
                }
                // TODO again setting out of `new()`
                e.box set it
            }

            val ids = boxEntities.map { it.id }

            // Warm up all the entities to fill the cache
            EBox.warmUpLinkedReferences(ids, BoxItem)

            val warmedUp = EBox.warmUpLinkedReferences(ids.slice(0..2), BoxItem)
            assertEquals(3, warmedUp.size)
        }
    }
}
