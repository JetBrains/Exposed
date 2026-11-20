@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.spring.controller

import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
internal class ControllerExceptionHandler {

    // A statement violated a database constraint, for example the unique index of IssuesTable.
    // Exposed throws an ExposedR2dbcException that has this exception from the database driver as its cause.
    // By the time it reaches this handler, the transaction that it was thrown in has been rolled back.
    @ExceptionHandler(R2dbcDataIntegrityViolationException::class)
    fun handleConstraintViolation(): ResponseEntity<Unit> {
        return ResponseEntity.status(HttpStatus.CONFLICT).build()
    }
}
