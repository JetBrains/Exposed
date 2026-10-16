@file:OptIn(InternalApi::class)

package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.spring.data.repository.ExposedRepositoryFactory
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Part types that have no meaning for a flat entity mapped to a single table (collections, geo, document stores),
 * plus invalid property paths. Repositories using them must fail when the repository is created, not on the first call.
 *
 * Needs no Spring context. The repositories are nested interfaces, so the package scan of
 * [SpringDataExposedTestConfiguration] doesn't pick them up.
 */
class ExposedSpringDataUnsupportedPartTypesTest {

    @ParameterizedTest
    @ValueSource(
        classes = [
            IsEmptyRepository::class,
            IsNotEmptyRepository::class,
            NearRepository::class,
            WithinRepository::class,
            ExistsRepository::class,
        ]
    )
    fun testUnsupportedPartTypeFailsAtCreation(repositoryInterface: Class<*>) {
        val expectedType = repositoryInterface.getAnnotation(ExpectedPartType::class.java).value
        val exception = assertThrows<Exception> { ExposedRepositoryFactory().getRepository(repositoryInterface) }
        val messages = generateSequence<Throwable>(exception) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue(messages.any { expectedType in it }) { "Expected a message mentioning $expectedType, got $messages" }
    }

    @Test
    fun testUnknownPropertyFailsAtCreation() {
        assertThrows<Exception> { ExposedRepositoryFactory().getRepository(UnknownPropertyRepository::class.java) }
    }

    @Test
    fun testNestedPropertyPathFailsAtCreation() {
        assertThrows<Exception> { ExposedRepositoryFactory().getRepository(NestedPathRepository::class.java) }
    }

    @Retention(AnnotationRetention.RUNTIME)
    annotation class ExpectedPartType(val value: String)

    @ExpectedPartType("IS_EMPTY")
    @ExposedRepository(table = Customers::class)
    interface IsEmptyRepository : ExposedCrudRepository<Customer, Long> {
        fun findByNicknameIsEmpty(): List<Customer>
    }

    @ExpectedPartType("IS_NOT_EMPTY")
    @ExposedRepository(table = Customers::class)
    interface IsNotEmptyRepository : ExposedCrudRepository<Customer, Long> {
        fun findByNicknameIsNotEmpty(): List<Customer>
    }

    @ExpectedPartType("NEAR")
    @ExposedRepository(table = Customers::class)
    interface NearRepository : ExposedCrudRepository<Customer, Long> {
        fun findByAgeNear(age: Int): List<Customer>
    }

    @ExpectedPartType("WITHIN")
    @ExposedRepository(table = Customers::class)
    interface WithinRepository : ExposedCrudRepository<Customer, Long> {
        fun findByAgeWithin(age: Int): List<Customer>
    }

    @ExpectedPartType("EXISTS")
    @ExposedRepository(table = Customers::class)
    interface ExistsRepository : ExposedCrudRepository<Customer, Long> {
        fun findByNicknameExists(): List<Customer>
    }

    @ExposedRepository(table = Customers::class)
    interface UnknownPropertyRepository : ExposedCrudRepository<Customer, Long> {
        fun findByEmial(email: String): Customer?
    }

    @ExposedRepository(table = Customers::class)
    interface NestedPathRepository : ExposedCrudRepository<Customer, Long> {
        fun findByEmailBytes(bytes: ByteArray): List<Customer>
    }
}
