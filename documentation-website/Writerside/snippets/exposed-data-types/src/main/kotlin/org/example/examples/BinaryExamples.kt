package org.example.examples

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction

/*
    Important: The code in this file is referenced by line number in `Binary-types.topic`.
    If you add, remove, or modify any lines prior to this one, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/

const val NAME_LENGTH = 255
const val THUMBNAIL_LENGTH = 1024

object Files : Table() {
    val id = integer("id").autoIncrement()
    val name = varchar("name", NAME_LENGTH)
    val content = blob("content")
    val thumbnail = binary("thumbnail", THUMBNAIL_LENGTH) // fixed-size binary data

    override val primaryKey = PrimaryKey(id)
}

class BinaryExamples {
    fun basicUsage() {
        transaction {
            SchemaUtils.create(Files)
            // Store binary data
            Files.insert {
                it[name] = "example.txt"
                it[content] = ExposedBlob("Hello, World!".toByteArray())
                it[thumbnail] = "thumbnail data".toByteArray()
            }

            // Read binary data
            val file = Files.selectAll().first()
            val content = file[Files.content]
            val text = String(content.bytes) // for small files
            // or
            val text2 = content.inputStream.bufferedReader().readText() // for large files

            val thumbnailBytes = file[Files.thumbnail] // returns ByteArray
            println(text, text2, thumbnailBytes)
        }
    }

    fun parameterBinding() {
        transaction {
            SchemaUtils.create(Files)
            // Efficient handling of binary data in queries
            Files.insert {
                it[name] = "example-param.txt"
                it[content] = blobParam(ExposedBlob("Some data".toByteArray()))
                it[thumbnail] = "thumbnail".toByteArray()
            }
        }
    }
}
