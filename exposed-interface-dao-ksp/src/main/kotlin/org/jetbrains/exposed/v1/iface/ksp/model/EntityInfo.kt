package org.jetbrains.exposed.v1.iface.ksp.model

import com.google.devtools.ksp.symbol.KSClassDeclaration

/**
 * Holds information about an entity interface being processed.
 *
 * @property declaration The KSP declaration of the interface.
 * @property packageName The package name where generated code will be placed.
 * @property interfaceName The name of the interface (e.g., "User" or "IUser").
 * @property tableName The database table name (e.g., "user").
 * @property schema Optional schema name.
 * @property generateDataClass Whether to generate immutable data class variant.
 * @property properties List of properties in the entity.
 * @property idProperty The primary key property.
 * @property hasCompanionObject Whether the interface declares a companion object.
 */
data class EntityInfo(
    val declaration: KSClassDeclaration,
    val packageName: String,
    val interfaceName: String,
    val tableName: String,
    val schema: String,
    val generateDataClass: Boolean,
    val properties: List<PropertyInfo>,
    val idProperty: PropertyInfo,
    val hasCompanionObject: Boolean = false
) {
    /**
     * Get the base name without 'I' prefix if present.
     * Examples:
     * - "User" -> "User"
     * - "IUser" -> "User" (backwards compatible)
     */
    val baseName: String
        get() = if (interfaceName.startsWith("I") && interfaceName.length > 1) {
            interfaceName.substring(1)
        } else {
            interfaceName
        }

    /**
     * Get the generated entity class name (e.g., "UserEntity").
     */
    val entityClassName: String
        get() = "${baseName}Entity"

    /**
     * Get the generated table object name (e.g., "UserTable").
     */
    val tableClassName: String
        get() = "${baseName}Table"

    /**
     * Get the generated data class name (e.g., "UserData").
     */
    val dataClassName: String
        get() = "${baseName}Data"
}
