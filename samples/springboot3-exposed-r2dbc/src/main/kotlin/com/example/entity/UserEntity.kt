package com.example.entity

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

internal object UserEntity : IntIdTable(name = "t_user") {
    internal val account: Column<String> = varchar(name = "account", length = 20)
    internal val password: Column<String> = varchar(name = "password", length = 64)
    internal val nickname: Column<String> = varchar(name = "nickname", length = 20)
}
