package org.jetbrains.exposed.v1.spring.data.autoconfigure

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.spring.data.ExposedCrudRepository
import org.jetbrains.exposed.v1.spring.data.ExposedRepository
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration

@SpringBootApplication(exclude = [DataSourceTransactionManagerAutoConfiguration::class])
open class StarterApplication

object CustomersTable : LongIdTable("starter_customers") {
    val name = varchar("name", 255)
}

data class Customer(val id: Long? = null, val name: String)

@ExposedRepository(table = CustomersTable::class)
interface CustomerRepository : ExposedCrudRepository<Customer, Long> {
    fun findByName(name: String): Customer?
}
