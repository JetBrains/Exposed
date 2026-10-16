package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.spring.data.query.query
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

open class CustomQueryTest : ExposedSpringDataTestBase() {
    @Autowired
    override lateinit var repository: CustomQueryRepository

    @Test
    fun testCustomQuery() {
        seed()
        val customers = repository.customQuery()
        assert(customers.size == 3)
        assert(customers.all { it.age >= 30 })
        val first = customers.first()
        assert(first.name == "Bob")
        val second = customers[1]
        assert(second.name == "Carla")
        val third = customers.last()
        assert(third.name == "Dan")
    }
}

@ExposedRepository(table = Customers::class)
interface CustomQueryRepository : ExposedCrudRepository<Customer, Long> {
    fun customQuery(): List<Customer> = query {
        Customers.selectAll()
            .where { Customers.age greaterEq 30 }
            .toList()
    }
}

