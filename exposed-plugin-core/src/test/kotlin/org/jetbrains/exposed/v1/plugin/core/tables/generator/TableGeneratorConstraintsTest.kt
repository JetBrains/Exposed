package org.jetbrains.exposed.v1.plugin.core.tables.generator

import org.jetbrains.exposed.v1.plugin.core.tables.TableGeneratorTestBase
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class TableGeneratorConstraintsTest : TableGeneratorTestBase() {
    @Test
    fun testCompositePrimaryKeyPreservesKeyOrder() {
        execute(
            """
            CREATE TABLE ORDER_ITEMS (
                QTY INT NOT NULL,
                ITEM_ID INT NOT NULL,
                ORDER_ID INT NOT NULL,
                PRIMARY KEY (ORDER_ID, ITEM_ID)
            )
            """.trimIndent()
        )

        val source = generatedSource(generate().single())

        assertContains(source, "override val primaryKey: Table.PrimaryKey = PrimaryKey(orderId, itemId)")
    }

    @Test
    fun testSingleAndMultiColumnIndices() {
        execute(
            """
            CREATE TABLE PRODUCTS (
                ID INT NOT NULL PRIMARY KEY,
                SKU VARCHAR(32) NOT NULL,
                CATEGORY VARCHAR(32) NOT NULL,
                NAME VARCHAR(64) NOT NULL
            )
            """.trimIndent(),
            // names matching Exposed's default naming (<table>_<columns>[_unique]) are omitted from the generated code
            "CREATE UNIQUE INDEX PRODUCTS_SKU_UNIQUE ON PRODUCTS (SKU)",
            "CREATE INDEX PRODUCTS_CATEGORY ON PRODUCTS (CATEGORY)",
            "CREATE INDEX PRODUCTS_CATEGORY_NAME ON PRODUCTS (CATEGORY, NAME)",
            "CREATE UNIQUE INDEX PRODUCTS_SKU_NAME_UNIQUE ON PRODUCTS (SKU, NAME)",
            // custom names are preserved, because Exposed's migration utilities compare index names
            "CREATE INDEX IDX_PRODUCTS_NAME ON PRODUCTS (NAME)",
            "CREATE UNIQUE INDEX UQ_PRODUCTS_SKU_CATEGORY ON PRODUCTS (SKU, CATEGORY)",
        )

        val source = generatedSource(generate().single())

        assertContains(source, "val id: Column<Int> = integer(\"ID\")")
        assertContains(source, "override val primaryKey: Table.PrimaryKey = PrimaryKey(id)")
        assertContains(source, "val sku: Column<String> = varchar(\"SKU\", 32).uniqueIndex()")
        assertContains(source, "val category: Column<String> = varchar(\"CATEGORY\", 32).index()")
        assertContains(source, "val name: Column<String> = varchar(\"NAME\", 64).index(\"IDX_PRODUCTS_NAME\")")
        assertContains(
            source,
            "init { index(false, category, name) uniqueIndex(sku, name) uniqueIndex(\"UQ_PRODUCTS_SKU_CATEGORY\", sku, category) }"
        )
    }

    @Test
    fun testPrimaryKeyNamesArePreservedOnlyWhenCustom() {
        execute(
            // H2 generates a CONSTRAINT_<n> name for an unnamed primary key
            "CREATE TABLE GENERATED_PK (ID INT NOT NULL PRIMARY KEY)",
            // pk_<table> is the name Exposed assigns by default, so it is omitted
            "CREATE TABLE DEFAULT_PK (ID INT NOT NULL, CONSTRAINT PK_DEFAULT_PK PRIMARY KEY (ID))",
            "CREATE TABLE CUSTOM_PK (ORDER_ID INT NOT NULL, ITEM_ID INT NOT NULL, CONSTRAINT PK_CUSTOM_ORDER_ITEM PRIMARY KEY (ORDER_ID, ITEM_ID))",
            "CREATE TABLE \"quoted_pk\" (ID INT NOT NULL, CONSTRAINT \"pk_quoted\" PRIMARY KEY (ID))",
        )

        val generated = generate()

        assertContains(generated.sourceOf("GeneratedPk"), "override val primaryKey: Table.PrimaryKey = PrimaryKey(id)")
        assertContains(generated.sourceOf("DefaultPk"), "override val primaryKey: Table.PrimaryKey = PrimaryKey(id)")
        assertContains(
            generated.sourceOf("CustomPk"),
            "override val primaryKey: Table.PrimaryKey = PrimaryKey(orderId, itemId, name = \"PK_CUSTOM_ORDER_ITEM\")"
        )
        assertContains(
            generated.sourceOf("QuotedPk"),
            "override val primaryKey: Table.PrimaryKey = PrimaryKey(id, name = \"\\\"pk_quoted\\\"\")"
        )
    }

    @Test
    fun testUniqueConstraintsKeepTheirConstraintName() {
        execute(
            """
            CREATE TABLE ACCOUNTS (
                ID INT NOT NULL PRIMARY KEY,
                EMAIL VARCHAR(320) NOT NULL,
                HANDLE VARCHAR(32) NOT NULL,
                NICK VARCHAR(32) NOT NULL,
                PHONE VARCHAR(20) NOT NULL
            )
            """.trimIndent(),
            // H2 backs a UNIQUE constraint with an index named <constraint>_INDEX_<n>,
            // so the definition must carry the constraint's name
            "ALTER TABLE ACCOUNTS ADD CONSTRAINT UQ_ACCOUNTS_EMAIL UNIQUE (EMAIL)",
            "ALTER TABLE ACCOUNTS ADD CONSTRAINT UQ_ACCOUNTS_EMAIL_HANDLE UNIQUE (EMAIL, HANDLE)",
            // a constraint named as by Exposed by default is regenerated without a name
            "ALTER TABLE ACCOUNTS ADD CONSTRAINT ACCOUNTS_PHONE_UNIQUE UNIQUE (PHONE)",
            // an unnamed constraint gets a generated CONSTRAINT_<n> name, which is omitted like any generated constraint name
            "ALTER TABLE ACCOUNTS ADD UNIQUE (NICK)",
        )

        val source = generate().sourceOf("Accounts")

        assertContains(source, "val email: Column<String> = varchar(\"EMAIL\", 320).uniqueIndex(\"UQ_ACCOUNTS_EMAIL\")")
        assertContains(source, "val nick: Column<String> = varchar(\"NICK\", 32).uniqueIndex()")
        assertContains(source, "val phone: Column<String> = varchar(\"PHONE\", 20).uniqueIndex()")
        assertContains(source, "init { uniqueIndex(\"UQ_ACCOUNTS_EMAIL_HANDLE\", email, handle) }")
        assertFalse(source.contains("_INDEX_"), "The name of a backing index must not leak into the definition: $source")
    }
}
