package org.jetbrains.exposed.v1.sql.tests.shared.entities

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.ImmutableCachedEntityClass
import org.jetbrains.exposed.v1.dao.ImmutableEntityClass
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.sql.tests.shared.assertEquals
import org.junit.Test

class ImmutableEntityTest : DatabaseTestsBase() {

    object Schema {
        object Organization : IdTable<Long>() {
            override val id = long("id").autoIncrement().entityId()
            val name = varchar("name", 256)
            val etag = long("etag").default(0)

            override val primaryKey = PrimaryKey(id)
        }
    }

    class EOrganization(id: EntityID<Long>) : Entity<Long>(id) {
        companion object : ImmutableEntityClass<Long, EOrganization>(Schema.Organization, EOrganization::class.java)

        val name by Schema.Organization.name
        val etag by Schema.Organization.etag
    }

    class ECachedOrganization(id: EntityID<Long>) : Entity<Long>(id) {
        companion object : ImmutableCachedEntityClass<Long, ECachedOrganization>(Schema.Organization, ECachedOrganization::class.java)

        val name by Schema.Organization.name
        val etag by Schema.Organization.etag
    }

    @Test
    fun immutableEntityReadAfterUpdate() {
        withTables(Schema.Organization, configure = { useNestedTransactions = true }) {
            transaction {
                Schema.Organization.insert {
                    it[name] = "JetBrains"
                    it[etag] = 0
                }
            }

            transaction {
                val org = EOrganization.all().single()

                EOrganization.forceUpdateEntity(org, Schema.Organization.etag, 1)

                assertEquals(1L, EOrganization.all().single().etag)
            }
        }
    }

    @Test
    fun immutableEntityCacheInvalidation() {
        withTables(Schema.Organization, configure = { useNestedTransactions = true }) {
            transaction {
                Schema.Organization.insert {
                    it[name] = "JetBrains"
                    it[etag] = 0
                }
            }

            transaction {
                Schema.Organization.update {
                    it[name] = "JetBrains Inc."
                }
            }

            transaction {
                val org = ECachedOrganization.all().single()

                ECachedOrganization.forceUpdateEntity(org, Schema.Organization.name, "JetBrains Gmbh")

                Schema.Organization.update {
                    it[etag] = 1
                }

                // Populate _cachedValues in ImmutableCachedEntityClass with inconsistent entity value
                ECachedOrganization.all().single()
            }

            transaction {
                val org = ECachedOrganization.all().single()

                assertEquals("JetBrains Gmbh", org.name)
                assertEquals(1L, org.etag)
            }
        }
    }
}
