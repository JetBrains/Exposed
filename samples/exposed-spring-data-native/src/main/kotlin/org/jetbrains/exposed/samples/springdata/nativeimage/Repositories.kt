package org.jetbrains.exposed.samples.springdata.nativeimage

import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.spring.data.ExposedCrudRepository
import org.jetbrains.exposed.v1.spring.data.ExposedRepository
import org.jetbrains.exposed.v1.spring.data.query.query
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.UUID

@ExposedRepository(table = Customers::class)
interface CustomerRepository : ExposedCrudRepository<Customer, Long> {
    fun findByEmail(email: String): Customer?

    fun findByAgeGreaterThan(age: Int, pageable: Pageable): Page<Customer>

    fun findByAgeGreaterThanOrderByAgeAsc(age: Int): List<CustomerName>

    fun findViewByEmail(email: String): CustomerView?

    /** Dynamic projection: the type is only known at call time, see [NativeSampleApplication]. */
    fun <T : Any> findByEmail(email: String, type: Class<T>): T?

    fun olderThan(age: Int): List<Customer> = query {
        Customers.selectAll().where { Customers.age greater age }.orderBy(Customers.age).toList()
    }

    fun namesByAge(): List<CustomerName> = query {
        Customers.select(Customers.name).orderBy(Customers.age).toList()
    }
}

@ExposedRepository(table = Notes::class)
interface NoteRepository : ExposedCrudRepository<Note, Long>

@ExposedRepository(table = Articles::class)
interface ArticleRepository : ExposedCrudRepository<Article, UUID> {
    fun findByTitle(title: String): Article?
}
