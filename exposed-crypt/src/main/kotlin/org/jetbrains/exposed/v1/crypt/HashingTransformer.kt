package org.jetbrains.exposed.v1.crypt

import org.jetbrains.exposed.v1.core.ColumnTransformer

class HashingTransformer(private val hasher: Hasher) : ColumnTransformer<String, Hashed> {
    override fun unwrap(value: Hashed): String = value.encodedValue

    override fun wrap(value: String): Hashed = Hashed(hasher, value)
}

class NullableHashingTransformer(private val hasher: Hasher) : ColumnTransformer<String?, Hashed?> {
    override fun unwrap(value: Hashed?): String? = value?.encodedValue

    override fun wrap(value: String?): Hashed? = value?.let { Hashed(hasher, it) }
}
