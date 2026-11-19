@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.spring

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension

@ExtendWith(OutputCaptureExtension::class)
internal class DatabaseConfigTest : ApplicationTest() {

    @Test
    fun `executed statements are logged by the custom logger`(output: CapturedOutput) {
        createUser(name = "John Smith", age = 52)

        val loggedInsert = output.out.lines().singleOrNull { "INSERT INTO users" in it && "John Smith" in it }

        assertTrue(loggedInsert != null && "SqlStatementLogger" in loggedInsert && "INFO" in loggedInsert)
    }
}
