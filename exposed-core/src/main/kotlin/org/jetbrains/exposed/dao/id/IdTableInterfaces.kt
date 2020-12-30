package org.jetbrains.exposed.dao.id

import org.jetbrains.exposed.sql.Column
import java.util.*


/**
 * Identity interface with autoincrement int primary key
 *
 * You should use IntIdTable implementation
 */
interface IntIdTableInterface: IdTableInterface<Int> {
    override val id: Column<EntityID<Int>>
}

/**
 * Identity interface with autoincrement long primary key
 *
 * You should use LongIdTable implementation
 */
interface LongIdTableInterface: IdTableInterface<Long> {
    override val id: Column<EntityID<Long>>
}

/**
 * Identity interface with autoincrement long primary key
 *
 * You should use UUIDIdTable implementation
 */
interface UUIDTableInterface: IdTableInterface<UUID> {
    override val id: Column<EntityID<UUID>>
}

