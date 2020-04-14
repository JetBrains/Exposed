package org.jetbrains.exposed.sql.tests.shared.entities

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.ImmutableCachedEntityClass
import org.jetbrains.exposed.dao.ImmutableEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
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

    @Test fun immutableEntityReadAfterUpdate() {
        withTables(Schema.Organization) {
            db.useNestedTransactions = true

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

    @Test fun immutableEntityCacheInvalidation() {
        withTables(Schema.Organization) {
            db.useNestedTransactions = true

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
                val updatedOrg = ECachedOrganization.all().single()
            }

            transaction {
                val org = ECachedOrganization.all().single()

                assertEquals("JetBrains Gmbh", org.name)
                assertEquals(1L, org.etag)
            }
        }
    }
}