package org.jetbrains.exposed.v1.r2dbc.mappers

/**
 * Sealed class representing the result of a getValue operation.
 * Contains either a present value or indicates that no value was provided.
 *
 * @param T The type of the value being retrieved.
 */
interface ValueContainer<T> {
    /**
     * True if the container holds a value, false otherwise.
     */
    val isPresent: Boolean

    /**
     * Retrieves the present value from the container.
     * @return The present value if [isPresent] is true.
     * @throws IllegalStateException if called on a container with no present value.
     */
    fun value(): T
}

/**
 * Represents a container with no present value.
 * This is used when the mapper cannot or should not provide a value.
 */
class NoValueContainer<T> : ValueContainer<T> {
    override val isPresent = false

    override fun value(): T = error("No value provided")
}

/**
 * Represents a container with a present value.
 *
 * @param value The present value to be contained.
 */
class PresentValueContainer<T>(val value: T) : ValueContainer<T> {
    override val isPresent = true

    override fun value() = value
}
