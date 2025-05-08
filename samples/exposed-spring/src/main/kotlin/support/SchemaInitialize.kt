@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.spring.support

import org.jetbrains.exposed.v1.samples.spring.domain.UserEntity
import org.jetbrains.exposed.v1.sql.SchemaUtils
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
class SchemaInitialize : ApplicationRunner {

    override fun run(args: ApplicationArguments?) {
        SchemaUtils.create(UserEntity)
    }
}
