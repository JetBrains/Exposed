package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.dml

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.r2dbc.ExperimentalR2dbcDaoApi
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass
import java.util.UUID

object Orgs : IntIdTable() {
    val uid = varchar("uid", 36).uniqueIndex().clientDefault { UUID.randomUUID().toString() }
    val name = varchar("name", 256)
}

@OptIn(ExperimentalR2dbcDaoApi::class)
class Org(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Org>(Orgs)

    var uid by Orgs.uid
    var name by Orgs.name
}

object OrgMemberships : IntIdTable() {
    val orgId = reference("org", Orgs.uid)
}

@OptIn(ExperimentalR2dbcDaoApi::class)
class OrgMembership(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<OrgMembership>(OrgMemberships)

    val orgId by OrgMemberships.orgId
    val org by Org referencedOn OrgMemberships.orgId
}
