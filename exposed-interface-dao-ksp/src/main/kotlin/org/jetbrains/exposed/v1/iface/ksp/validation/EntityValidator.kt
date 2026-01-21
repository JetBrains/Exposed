package org.jetbrains.exposed.v1.iface.ksp.validation

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration

/**
 * Validates entity interface declarations.
 */
object EntityValidator {
    fun validate(classDeclaration: KSClassDeclaration, logger: KSPLogger) {
        val name = classDeclaration.simpleName.asString()

        // Must be an interface
        if (classDeclaration.classKind != ClassKind.INTERFACE) {
            logger.error("@ExposedEntity can only be applied to interfaces, but $name is a ${classDeclaration.classKind}", classDeclaration)
            return
        }

        // Note: We no longer require an explicit 'id' property since entities inherit
        // from IntEntity/LongEntity which provides the id. If no id property is declared,
        // a synthetic one will be created during code generation.

        logger.info("Entity $name validated successfully")
    }
}
