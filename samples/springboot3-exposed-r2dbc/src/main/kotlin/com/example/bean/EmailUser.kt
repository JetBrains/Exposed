package com.example.bean

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "email")
@ConsistentCopyVisibility
internal data class EmailUser internal constructor(
    internal val user: String,
    internal val email: String
)
