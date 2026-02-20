package com.example.exposedspring

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

object MessageEntity : LongIdTable() {
    val text = varchar("text", length = 250)
}
