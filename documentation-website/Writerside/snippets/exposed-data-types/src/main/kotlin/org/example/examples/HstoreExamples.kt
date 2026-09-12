@file:Suppress("MagicNumber")

package org.example.examples

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.postgresql.hstore.concat
import org.jetbrains.exposed.v1.postgresql.hstore.contains
import org.jetbrains.exposed.v1.postgresql.hstore.delete
import org.jetbrains.exposed.v1.postgresql.hstore.exists
import org.jetbrains.exposed.v1.postgresql.hstore.existsAll
import org.jetbrains.exposed.v1.postgresql.hstore.existsAny
import org.jetbrains.exposed.v1.postgresql.hstore.get
import org.jetbrains.exposed.v1.postgresql.hstore.hstore

/*
 * Important: The code in this file is referenced by line number in `hstore-type.md`.
 * If you add, remove, or modify any lines prior to this one, ensure you update the corresponding
 * line numbers in the `code-block` element of the referenced file.
 */

object ProductsTable : Table("products") {
    val name = varchar("name", 64)
    val attributes = hstore("attributes")
}

class HstoreExamples {
    fun example() {
        ProductsTable.insert {
            it[name] = "Keyboard"
            it[attributes] = mapOf("color" to "black", "layout" to "qwerty")
        }

        ProductsTable
            .selectAll()
            .map { "${it[ProductsTable.name]} -> ${it[ProductsTable.attributes]}" }
            .forEach { println(it) }
        // Keyboard -> {color=black, layout=qwerty}
    }

    fun update() {
        ProductsTable.update({ ProductsTable.name eq "Keyboard" }) {
            it[attributes] = mapOf("color" to "white", "layout" to "qwerty")
        }
    }

    fun useGet() {
        val color = ProductsTable.attributes.get("color")
        val colors = ProductsTable.select(color).map { it[color] }
        println(colors)
    }

    fun useContains() {
        val isBlack = ProductsTable.attributes.contains(mapOf("color" to "black"))
        val blackProducts = ProductsTable.selectAll().where { isBlack }.count()
        println(blackProducts)
    }

    fun useExists() {
        val hasColor = ProductsTable.attributes.exists("color")
        val withColor = ProductsTable.selectAll().where { hasColor }.count()
        println(withColor)
    }

    fun useExistsAllAndAny() {
        val hasColorAndLayout = ProductsTable.attributes.existsAll(listOf("color", "layout"))
        val hasColorOrSize = ProductsTable.attributes.existsAny(listOf("color", "size"))
        val both = ProductsTable.selectAll().where { hasColorAndLayout }.count()
        val either = ProductsTable.selectAll().where { hasColorOrSize }.count()
        println("$both $either")
    }

    fun useDelete() {
        val withoutLayout = ProductsTable.attributes.delete("layout")
        val remaining = ProductsTable.select(withoutLayout).map { it[withoutLayout] }
        println(remaining)
    }

    fun useConcat() {
        val withSize = ProductsTable.attributes.concat(mapOf("size" to "M"))
        val merged = ProductsTable.select(withSize).map { it[withSize] }
        println(merged)
    }
}
