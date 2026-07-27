package org.jetbrains.exposed.v1.maven.plugin.integrations

import org.jetbrains.exposed.v1.maven.plugin.integrations.helpers.ExposedMigrationsConfig
import org.jetbrains.exposed.v1.maven.plugin.integrations.helpers.MavenGoal
import org.jetbrains.exposed.v1.maven.plugin.integrations.helpers.MavenProcessResult
import org.jetbrains.exposed.v1.maven.plugin.integrations.helpers.TestMavenProject
import org.jetbrains.exposed.v1.plugin.core.migration.VersionFormat
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Execution(ExecutionMode.SAME_THREAD)
class MavenProcessTest {

    @Test
    fun `it should fail with message if misconfigured`() = TestMavenProject {
        configure { }
        verify {
            val failure = executeGoal(MavenGoal.GenerateMigrations).assertFailure()
            assertTrue { failure.output.contains("Unable to generate migration, likely due to misconfiguration") }
        }
    }

    @Test
    fun `it should generate migrations using h2`() = TestMavenProject {
        configure {
            migrationsConfig {
                h2()
            }
        }

        verify {
            executeGoal(MavenGoal.GenerateMigrations).assertSuccess()
            assertEquals(2, migrations.size)
        }
    }

    @Test
    fun `it should generate migrations using testcontainers`() = TestMavenProject {
        configure {
            migrationsConfig {
                testContainersImageName = "postgres:14"
            }
        }

        verify {
            val result = executeGoal(MavenGoal.GenerateMigrations)
            assertIs<MavenProcessResult.Success>(result)
            assertEquals(2, migrations.size)
        }
    }

    @Test
    fun `it should generate lowercase migration names`() = TestMavenProject {
        configure {
            migrationsConfig {
                h2()
                useUpperCaseDescription = "false"
            }
        }

        verify {
            executeGoal(MavenGoal.GenerateMigrations).assertSuccess()
            assertEquals(2, migrations.size)
            assertTrue {
                migrations
                    .map { it.name.drop(1) }
                    .all { it == it.lowercase() }
            }
        }
    }

    @Test
    fun `it should generate migrations with custom file extension`() = TestMavenProject {
        configure {
            migrationsConfig {
                h2()
                fileExtension = ".ddl"
            }
        }

        verify {
            executeGoal(MavenGoal.GenerateMigrations).assertSuccess()
            assertEquals(2, migrations.size)
            assertTrue {
                migrations
                    .map { it.name }
                    .all { it.endsWith(".ddl") }
            }
        }
    }

    @Test
    fun `it should generate migrations with custom prefix`() = TestMavenProject {
        configure {
            migrationsConfig {
                h2()
                filePrefix = "VVV1"
            }
        }

        verify {
            executeGoal(MavenGoal.GenerateMigrations).assertSuccess()
            assertEquals(2, migrations.size)
            assertTrue {
                migrations
                    .map { it.name }
                    .all { it.startsWith("VVV1") }
            }
        }
    }

    @Test
    fun `it should generate a single migration with exact name when fullFileName was configured`() = TestMavenProject {
        configure {
            migrationsConfig {
                h2()
                fullFileName = "goblins_are_great.sql"
            }
        }

        verify {
            executeGoal(MavenGoal.GenerateMigrations).assertSuccess()
            assertEquals(1, migrations.size)
            assertTrue {
                migrations
                    .map { it.name }
                    .all { it == "goblins_are_great.sql" }
            }
        }
    }

    @Test
    fun `it should generate migrations that adhere to file version format`() = TestMavenProject {
        configure {
            migrationsConfig {
                h2()
                // Flyway file version format
                fileVersionFormat = VersionFormat.TIMESTAMP_ONLY
            }
        }

        verify {
            executeGoal(MavenGoal.GenerateMigrations).assertSuccess()
            assertEquals(2, migrations.size)
            val now = OffsetDateTime.now()
            val expected = "%04d%02d%02d".format(
                now.year,
                now.monthValue,
                now.dayOfMonth
            )
            assertTrue {
                migrations
                    .map { it.name }
                    .all { expected in it }
            }
        }
    }

    @Test
    fun `it should generate migrations using correct file separator`() = TestMavenProject("test") {
        configure {
            migrationsConfig {
                h2()
                fileSeparator = "###"
            }
        }

        verify {
            executeGoal(MavenGoal.GenerateMigrations).assertSuccess()
            assertTrue {
                migrations
                    .map { it.name }
                    .all { "###" in it }
            }
        }
    }

    @Test
    fun `it should generate no migrations if it doesn't find any tables to generate`() = TestMavenProject {
        configure {
            migrationsConfig {
                h2()
                tablesPackage = "something.wrong"
            }
        }

        verify {
            executeGoal(MavenGoal.GenerateMigrations).assertSuccess()
            assertEquals(0, migrations.size)
        }
    }

    @Test
    fun `it should generate no migrations if none are needed when running in testcontainers`() = TestMavenProject {
        configure {
            migrationsConfig {
                testContainersImageName = "postgres:14"
                withExistingMigrations {
                    dialect = "pg"
                    allMigrations()
                }
            }
        }

        verify {
            assertEquals(2, migrations.size)
            executeGoal(MavenGoal.GenerateMigrations).assertSuccess()
            assertEquals(2, migrations.size)
        }
    }

    @Test
    fun `it should generate only migrations that are needed when running in Testcontainer`() = TestMavenProject {
        configure {
            migrationsConfig {
                testContainersImageName = "postgres:14"
                withExistingMigrations {
                    dialect = "pg"
                    cities = true
                    users = false
                }
            }
        }

        verify {
            assertEquals(1, migrations.size)
            executeGoal(MavenGoal.GenerateMigrations).assertSuccess()
            assertEquals(2, migrations.size)
        }
    }

    private fun ExposedMigrationsConfig.h2() {
        databaseUrl = "jdbc:h2:mem:test"
        databaseUser = "sa"
        databasePassword = "sa"
    }
}
