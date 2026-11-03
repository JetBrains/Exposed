package org.jetbrains.exposed.v1.spring.data

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ExposedRepository(
    val table: KClass<*>
)
