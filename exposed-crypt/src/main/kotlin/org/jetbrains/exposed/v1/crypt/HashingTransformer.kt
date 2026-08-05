package org.jetbrains.exposed.v1.crypt

import org.jetbrains.exposed.v1.core.ColumnTransformer

/**
 * Transformer that stores the [Hashed.encodedValue] of a hashed value in a character column, using the provided
 * [hasher] to verify the values read back out of it.
 *
 * The hashing itself is performed by [Hasher.hash], not by this transformer, so a value that is already stored is
 * never hashed again when it is read and written back.
 */
class HashingTransformer(
    /** [Hasher] this transformer hashes with, and verifies the values it reads against. */
    val hasher: Hasher
) : ColumnTransformer<String, Hashed> {
    override fun unwrap(value: Hashed): String = value.encodedValue

    override fun wrap(value: String): Hashed = Hashed(hasher, value)
}

/**
 * Transformer that behaves like [HashingTransformer] but passes `null` through untouched, for use with nullable
 * character columns.
 */
class NullableHashingTransformer(
    /** [Hasher] this transformer hashes with, and verifies the values it reads against. */
    val hasher: Hasher
) : ColumnTransformer<String?, Hashed?> {
    override fun unwrap(value: Hashed?): String? = value?.encodedValue

    override fun wrap(value: String?): Hashed? = value?.let { Hashed(hasher, it) }
}
