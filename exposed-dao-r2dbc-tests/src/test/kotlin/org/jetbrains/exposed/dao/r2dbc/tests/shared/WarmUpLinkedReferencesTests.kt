package org.jetbrains.exposed.dao.r2dbc.tests.shared

import org.jetbrains.exposed.r2dbc.dao.IntEntity
import org.jetbrains.exposed.r2dbc.dao.IntEntityClass
import org.jetbrains.exposed.r2dbc.dao.flushCache
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import kotlin.test.Test

class WarmUpLinkedReferencesTests : R2dbcDatabaseTestsBase() {

    object Box : IntIdTable() {
        val value = integer("value")
    }

    class EBox(id: EntityID<Int>) : IntEntity(id) {
        var value by Box.value

        companion object : IntEntityClass<EBox>(Box)
    }

    object BoxItem : IntIdTable() {
        val box = reference("box", Box)

        val value = integer("value")
    }

    class EBoxItem(id: EntityID<Int>) : IntEntity(id) {
        var value by BoxItem.value
        val box by EBox referencedOn BoxItem.box

        companion object : IntEntityClass<EBoxItem>(BoxItem)
    }

    @Test
    fun warmUpLinkedReferencesShouldNotReturnAllTheValueFromCache() {
        withTables(Box, BoxItem) {
            val boxEntities = (0..4).map {
                EBox.new {
                    value = it
                }.flush()
            }

            boxEntities.forEach {
                val e = EBoxItem.new {
                    value = it.id.value
                }.initializedEntity
                e.box.set(it)
            }
            flushCache()

            val ids = boxEntities.map { it.id }

            // Warm up all the entities to fill the cache
            EBox.warmUpLinkedReferences(ids, BoxItem)

            val warmedUp = EBox.warmUpLinkedReferences(ids.slice(0..2), BoxItem)
            assertEquals(3, warmedUp.size)
        }
    }
}
