package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.id.EntityID


interface IdAware<ID : Comparable<ID>> {

    val id: Column<EntityID<ID>>

}
