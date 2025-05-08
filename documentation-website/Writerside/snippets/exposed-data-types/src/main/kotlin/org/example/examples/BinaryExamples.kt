package org.example.examples

import org.jetbrains.exposed.v1.sql.*
import org.jetbrains.exposed.v1.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.sql.transactions.transaction

/*
    Important: The code in this file is referenced by line number in `Binary-types.topic`.
    If you add, remove, or modify any lines prior to this one, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/
@Suppress("MagicNumber")
class BinaryExamples {
    object Files : Table() {
        val id = integer("id").autoIncrement()
        val name = varchar("name", 255)
        val content = blob("content")
        val simpleData = binary("simple_data") // simple version without length
        val thumbnail = binary("thumbnail", 1024) // length-specified version

        override val primaryKey = PrimaryKey(id)
    }

    fun basicUsage() {
        transaction {
            SchemaUtils.create(Files)
            // Store binary data
            Files.insert {
                it[name] = "example.txt"
                it[content] = ExposedBlob("Hello, World!".toByteArray())
                it[simpleData] = "simple binary data".toByteArray() // using simple binary version
                it[thumbnail] = "thumbnail data".toByteArray() // using length-specified version
            }

            // Read binary data
            val file = Files.selectAll().first()
            val content = file[Files.content]
            val text = String(content.bytes) // for small files
            // or
            val text2 = content.inputStream.bufferedReader().readText() // for large files

            // Read both types of binary data
            val simpleBytes = file[Files.simpleData] // returns ByteArray from simple binary
            val thumbnailBytes = file[Files.thumbnail] // returns ByteArray from length-specified binary
            println("$text, $text2")
            println(simpleBytes)
            println(thumbnailBytes)
        }
    }

    fun parameterBinding() {
        transaction {
            SchemaUtils.create(Files)
            // Efficient handling of binary data in queries
            Files.insert {
                it[name] = "example-param.txt"
                it[simpleData] = "simple binary data".toByteArray() // using simple binary version
                it[content] = blobParam(ExposedBlob("Some data".toByteArray()))
                it[thumbnail] = "thumbnail".toByteArray()
            }
        }
    }
}
