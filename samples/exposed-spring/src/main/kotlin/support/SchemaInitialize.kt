@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.spring.support

import org.jetbrains.exposed.samples.spring.domain.UserEntity
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Component
class SchemaInitialize(
    private val transactionManager: PlatformTransactionManager
) : InitializingBean {

    override fun afterPropertiesSet() {
        TransactionTemplate(transactionManager).execute {
            SchemaUtils.create(UserEntity)
        }
    }
}
