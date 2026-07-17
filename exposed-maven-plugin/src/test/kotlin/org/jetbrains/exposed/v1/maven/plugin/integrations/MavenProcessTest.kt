package org.jetbrains.exposed.v1.maven.plugin.integrations

import org.jetbrains.exposed.v1.maven.plugin.integrations.helpers.MavenGoal
import org.jetbrains.exposed.v1.maven.plugin.integrations.helpers.MavenProcessResult
import org.jetbrains.exposed.v1.maven.plugin.integrations.helpers.TestMavenProject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MavenProcessTest {

    @Test
    fun `test generate basic migrations`() = TestMavenProject("tmp") {
        migrationsConfig {
            databaseUrl = "jdbc:h2:mem:test"
            databaseUser = "sa"
            databasePassword = "sa"
        }
        generate()
        val result = executeGoal(MavenGoal.GenerateMigrations)
        assertIs<MavenProcessResult.Success>(result)
        assertEquals(2, migrations.size)
    }
}
