package org.jetbrains.exposed.gradle

@Deprecated("Use gradle version catalog", level = DeprecationLevel.ERROR)
object Versions {
    const val kotlinCoroutines = "1.7.3"
    const val kotlinxSerialization = "1.5.1"

    const val slf4j = "1.7.36"
    const val log4j2 = "2.20.0"

    /** JDBC drivers **/
    const val h2 = "1.4.200"
    const val h2_v2 = "2.2.220"
    const val mariaDB_v2 = "2.7.9"
    const val mariaDB_v3 = "3.1.4"
    const val mysql51 = "5.1.49"
    const val mysql80 = "8.0.33"
    const val oracle12 = "12.2.0.1"
    const val postgre = "42.6.0"
    const val postgreNG = "0.8.9"
    const val sqlLite3 = "3.43.0.0"
    const val sqlserver = "9.4.1.jre8"

    /** Spring **/
    const val springFramework = "6.0.11"
    const val springBoot = "3.1.3"
}
