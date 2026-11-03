package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.spring.data.repository.ExposedRepositoryFactory
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Part types that have no meaning for a flat entity mapped to a single table (collections, geo, document stores),
 * plus invalid property paths, also nested ones (see [DerivedQueryJoinTest] for the valid ones).
 * Repositories using them must fail when the repository is created, not on the first call.
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
    fun testNestedPathThroughNonReferenceFailsAtCreation() {
        assertFailsMentioning(NestedPathRepository::class.java, "email")
    }

    @Test
    fun testUnknownNestedPropertyFailsAtCreation() {
        assertThrows<Exception> { ExposedRepositoryFactory().getRepository(UnknownNestedPropertyRepository::class.java) }
    }

    @Test
    fun testToManyWithoutReferencedByFailsAtCreation() {
        assertFailsMentioning(MissingReferencedByRepository::class.java, "@ExposedReferencedBy")
    }

    @Test
    fun testAmbiguousForeignKeyFailsAtCreation() {
        assertFailsMentioning(AmbiguousForeignKeyRepository::class.java, "column")
    }

    @Test
    fun testReferencedByTableWithoutForeignKeyFailsAtCreation() {
        assertFailsMentioning(NoForeignKeyRepository::class.java, "products")
    }

    @Test
    fun testReferencedByUnknownColumnFailsAtCreation() {
        assertFailsMentioning(UnknownReferencedByColumnRepository::class.java, "doesNotExist")
    }

    private fun assertFailsMentioning(repositoryInterface: Class<*>, expected: String) {
        val exception = assertThrows<Exception> { ExposedRepositoryFactory().getRepository(repositoryInterface) }
        val messages = generateSequence<Throwable>(exception) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue(messages.any { expected in it }) { "Expected a message mentioning $expected, got $messages" }
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

    @ExposedRepository(table = Orders::class)
    interface UnknownNestedPropertyRepository : ExposedCrudRepository<Order, Long> {
        fun findByItemsNmae(name: String): List<Order>
    }

    data class UnannotatedOrder(val id: Long? = null, val customer: Long, val note: String, val items: List<OrderItem> = emptyList())

    @ExposedRepository(table = Orders::class)
    interface MissingReferencedByRepository : ExposedCrudRepository<UnannotatedOrder, Long> {
        fun findByItemsName(name: String): List<UnannotatedOrder>
    }

    data class AmbiguousOrder(
        val id: Long? = null,
        val customer: Long,
        val note: String,
        @ExposedReferencedBy(OrderComments::class) val comments: List<OrderComment> = emptyList(),
    )

    @ExposedRepository(table = Orders::class)
    interface AmbiguousForeignKeyRepository : ExposedCrudRepository<AmbiguousOrder, Long> {
        fun findByCommentsText(text: String): List<AmbiguousOrder>
    }

    data class NoForeignKeyOrder(
        val id: Long? = null,
        val customer: Long,
        val note: String,
        @ExposedReferencedBy(Products::class) val products: List<Product> = emptyList(),
    )

    @ExposedRepository(table = Orders::class)
    interface NoForeignKeyRepository : ExposedCrudRepository<NoForeignKeyOrder, Long> {
        fun findByProductsName(name: String): List<NoForeignKeyOrder>
    }

    data class UnknownColumnOrder(
        val id: Long? = null,
        val customer: Long,
        val note: String,
        @ExposedReferencedBy(OrderComments::class, column = "doesNotExist")
        val comments: List<OrderComment> = emptyList(),
    )

    @ExposedRepository(table = Orders::class)
    interface UnknownReferencedByColumnRepository : ExposedCrudRepository<UnknownColumnOrder, Long> {
        fun findByCommentsText(text: String): List<UnknownColumnOrder>
    }
}
