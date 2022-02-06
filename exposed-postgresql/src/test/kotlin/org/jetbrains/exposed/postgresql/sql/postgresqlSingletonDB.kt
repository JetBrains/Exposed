package org.jetbrains.exposed.postgresql.sql

import org.testcontainers.containers.PostgreSQLContainer

object PostgresSingletonContainer : PostgreSQLContainer<PostgresSingletonContainer>("postgres:12.6-alpine") {

    init {
        //granted permissions, schema etc
        withInitScript("init-test.sql")
        addExposedPort(5432)

        withDatabaseName("postgres")
        withUsername("usr")
        withPassword("pwd")

        start()
    }
}