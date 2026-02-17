package com.example.exposedspring

import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
class SchemaInitialize : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        SchemaUtils.create(MessageEntity)
    }
}