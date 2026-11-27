package org.jetbrains.exposed.v1.plugin.core.tables

import org.jetbrains.exposed.v1.plugin.core.tables.mapping.ExposedModule

/**
 * The Exposed date-time extension module that should be used as the source of [org.jetbrains.exposed.v1.core.Table]
 * methods that return date-time [org.jetbrains.exposed.v1.core.Column]s when generating table definitions.
 */
enum class DateTimeColumnType(
    /** Package of the module's column functions from which they are imported in generated code. */
    internal val functionPackage: String,
    /** The module itself for detecting it on a classpath and naming it in log messages. */
    internal val module: ExposedModule,
) {
    /**
     * Maps to the functions of `exposed-kotlin-datetime`, which store `kotlinx.datetime` values
     * (note that `timestampWithTimeZone()` stores [java.time.OffsetDateTime]).
     */
    KOTLIN_DATETIME(functionPackage = "org.jetbrains.exposed.v1.datetime", module = ExposedModule.KOTLIN_DATETIME),

    /**
     * Maps to the functions of `exposed-java-time`, which store `java.time` values.
     */
    JAVA_TIME(functionPackage = "org.jetbrains.exposed.v1.javatime", module = ExposedModule.JAVA_TIME),

    /**
     * Maps to the functions of `exposed-jodatime`, which store `org.joda.time` values.
     */
    JODA_TIME(functionPackage = "org.jetbrains.exposed.v1.jodatime", module = ExposedModule.JODA_TIME),
}
