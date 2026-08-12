package org.jetbrains.exposed.v1.jdbc.vendors

/**
 * Amazon Redshift dialect metadata implementation.
 */
open class RedshiftDialectMetadata : DatabaseDialectMetadata() {
    override fun supportsLimitWithUpdateOrDelete(): Boolean = false
}
