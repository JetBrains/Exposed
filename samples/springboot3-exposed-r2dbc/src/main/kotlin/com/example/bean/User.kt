package com.example.bean

import com.fasterxml.jackson.annotation.JsonAutoDetect

@JsonAutoDetect(
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE,
    fieldVisibility = JsonAutoDetect.Visibility.ANY // 允许访问所有字段
)
@ConsistentCopyVisibility
internal data class User internal constructor(
    internal val id: Int,
    internal val account: String,
    internal val password: String,
    internal val nickname: String,
)
