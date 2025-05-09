package org.jetbrains.exposed.v1.core.statements.api

/**
 * Base class representing a savepoint, with the specified [name], which can be referenced during a
 * rollback operation to undo any changes made after the point in the transaction.
 */
@Suppress("UnnecessaryAbstractClass")
abstract class ExposedSavepoint(val name: String)
