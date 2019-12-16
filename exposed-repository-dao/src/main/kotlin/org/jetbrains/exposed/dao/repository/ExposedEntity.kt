package org.jetbrains.exposed.dao.repository

interface ExposedEntity<ID:Any, T:ExposedEntity<ID, T>> {
    var id : ID?
}

data class FooEntity(override var id: Int?) : ExposedEntity<Int, FooEntity>