package org.jetbrains.exposed.v1.plugin.core.tables.generator

import org.jetbrains.exposed.v1.plugin.core.tables.ColumnMapping
import org.jetbrains.exposed.v1.plugin.core.tables.TableGeneratorTestBase
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TableGeneratorForeignKeyTest : TableGeneratorTestBase() {
    @Test
    fun testSingleColumnForeignKeysBecomeReferences() {
        execute(
            "CREATE SCHEMA SALES",
            "CREATE TABLE USERS (ID INT NOT NULL PRIMARY KEY, EMAIL VARCHAR(50) NOT NULL UNIQUE)",
            """
            CREATE TABLE ORDERS (
                ID INT NOT NULL PRIMARY KEY,
                USER_ID INT NOT NULL,
                OWNER_EMAIL VARCHAR(50) NULL,
                PARENT_ID INT NULL,
                CONSTRAINT FK_ORDERS_USER FOREIGN KEY (USER_ID) REFERENCES USERS (ID) ON DELETE CASCADE ON UPDATE RESTRICT,
                FOREIGN KEY (OWNER_EMAIL) REFERENCES USERS (EMAIL),
                FOREIGN KEY (PARENT_ID) REFERENCES ORDERS (ID) ON DELETE SET NULL ON UPDATE RESTRICT
            )
            """.trimIndent(),
            "CREATE TABLE SALES.INVOICES (ID INT NOT NULL PRIMARY KEY, ORDER_ID INT NOT NULL REFERENCES PUBLIC.ORDERS (ID) ON DELETE RESTRICT ON UPDATE RESTRICT)",
        )

        val generated = generate(schemas = listOf("PUBLIC", "SALES"))

        assertEquals(listOf(path("Orders"), path("Users"), path("SalesInvoices")), generated)
        val ordersSource = generatedSource(generated[0])
        assertContains(
            ordersSource,
            "val userId: Column<Int> = integer(\"USER_ID\").references(Users.id, onDelete = ReferenceOption.CASCADE, fkName = \"FK_ORDERS_USER\")"
        )
        assertContains(
            ordersSource,
            "val ownerEmail: Column<String?> = varchar(\"OWNER_EMAIL\", 50)" +
                ".references(Users.email, onDelete = ReferenceOption.NO_ACTION, onUpdate = ReferenceOption.NO_ACTION).nullable()"
        )
        assertContains(
            ordersSource,
            "val parentId: Column<Int?> = integer(\"PARENT_ID\").references(id, onDelete = ReferenceOption.SET_NULL).nullable()"
        )
        assertContains(ordersSource, "import org.jetbrains.exposed.v1.core.ReferenceOption")

        assertFalse(ordersSource.contains("_INDEX_"), "Foreign key backing indices must not be generated: $ordersSource")
        assertFalse(ordersSource.contains(".index("), "No index expected on the key columns: $ordersSource")

        val invoicesSource = generatedSource(generated[2])
        assertContains(invoicesSource, "val orderId: Column<Int> = integer(\"ORDER_ID\").references(Orders.id)")
        assertFalse(invoicesSource.contains("ReferenceOption"), "No rule expected for the default rules: $invoicesSource")
    }

    @Test
    fun testForeignKeyRulesAreDeclaredOnlyWhenTheyDifferFromTheDefault() {
        execute(
            "CREATE TABLE USERS (ID INT NOT NULL PRIMARY KEY)",
            """
            CREATE TABLE AUDITS (
                ID INT NOT NULL PRIMARY KEY,
                ACTOR_ID INT NULL,
                REVIEWER_ID INT NULL,
                FOREIGN KEY (ACTOR_ID) REFERENCES USERS (ID) ON DELETE RESTRICT ON UPDATE CASCADE,
                FOREIGN KEY (REVIEWER_ID) REFERENCES USERS (ID) ON DELETE SET DEFAULT ON UPDATE NO ACTION
            )
            """.trimIndent(),
        )

        val source = generate().sourceOf("Audits")

        assertContains(
            source,
            "val actorId: Column<Int?> = integer(\"ACTOR_ID\").references(Users.id, onUpdate = ReferenceOption.CASCADE).nullable()"
        )
        assertContains(
            source,
            "val reviewerId: Column<Int?> = integer(\"REVIEWER_ID\")" +
                ".references(Users.id, onDelete = ReferenceOption.SET_DEFAULT, onUpdate = ReferenceOption.NO_ACTION).nullable()"
        )
    }

    @Test
    fun testCompositeKeysAreGeneratedInTheInitBlockAndUnresolvableKeysAreReported() {
        execute(
            "CREATE SCHEMA ARCHIVE",
            "CREATE TABLE ARCHIVE.LOGS (ID INT NOT NULL PRIMARY KEY)",
            """
            CREATE TABLE PARTS (
                MAKER VARCHAR(10) NOT NULL,
                CODE VARCHAR(10) NOT NULL,
                SPAN INTERVAL DAY NULL,
                PRIMARY KEY (MAKER, CODE),
                UNIQUE (SPAN),
                UNIQUE (MAKER, SPAN)
            )
            """.trimIndent(),
            """
            CREATE TABLE EVENTS (
                ID INT NOT NULL PRIMARY KEY,
                LOG_ID INT NOT NULL REFERENCES ARCHIVE.LOGS (ID),
                PART_SPAN INTERVAL DAY NULL REFERENCES PARTS (SPAN),
                PART_MAKER VARCHAR(10) NOT NULL,
                PART_CODE VARCHAR(10) NOT NULL,
                OTHER_MAKER VARCHAR(10) NULL,
                CONSTRAINT FK_EVENTS_PART FOREIGN KEY (PART_MAKER, PART_CODE) REFERENCES PARTS (MAKER, CODE) ON DELETE CASCADE ON UPDATE RESTRICT,
                FOREIGN KEY (OTHER_MAKER, PART_SPAN) REFERENCES PARTS (MAKER, SPAN)
            )
            """.trimIndent(),
        )

        // the ARCHIVE schema is not searched, so LOGS is not generated
        val source = generate().sourceOf("Events")

        // a single-column key to a table that is not generated: the column is generated without the reference, and the key is reported
        assertContains(source, "val logId: Column<Int> = integer(\"LOG_ID\")")
        assertFalse(source.contains("references(Logs"), "A reference to a table that is not generated must not be rendered: $source")
        assertContains(
            source,
            "TODO: Foreign key of column 'LOG_ID' of table 'EVENTS' references table 'ARCHIVE.LOGS' is not generated"
        )
        assertWarning("Foreign key of column 'LOG_ID' of table 'EVENTS' references table 'ARCHIVE.LOGS' is not generated")
        assertFalse(source.contains("partSpan"), "Unsupported column should not be generated: $source")
        // a composite key is generated in the init block, pairing each column with its target
        assertContains(source, "val partMaker: Column<String> = varchar(\"PART_MAKER\", 10)")
        assertContains(
            source,
            "init { foreignKey(partMaker to Parts.maker, partCode to Parts.code, onDelete = ReferenceOption.CASCADE, name = \"FK_EVENTS_PART\") }"
        )
        // a composite key including a column that was not generated is reported on the object
        assertContains(
            source,
            "TODO: Foreign key of table 'EVENTS' on columns OTHER_MAKER, PART_SPAN includes a column that was not generated"
        )
        assertTrue(
            messages.any { it.contains("includes a column that was not generated") },
            "Expected a warning about the skipped key: $messages"
        )
    }

    @Test
    fun testCompositeKeyTargetingAnotherSchemaAndItself() {
        execute(
            "CREATE SCHEMA SALES",
            "CREATE TABLE SALES.REGIONS (COUNTRY CHAR(2) NOT NULL, AREA VARCHAR(10) NOT NULL, PRIMARY KEY (COUNTRY, AREA))",
            """
            CREATE TABLE STORES (
                ID INT NOT NULL PRIMARY KEY,
                COUNTRY CHAR(2) NOT NULL,
                AREA VARCHAR(10) NOT NULL,
                PARENT_COUNTRY CHAR(2) NULL,
                PARENT_AREA VARCHAR(10) NULL,
                UNIQUE (COUNTRY, AREA),
                FOREIGN KEY (COUNTRY, AREA) REFERENCES SALES.REGIONS (COUNTRY, AREA) ON DELETE RESTRICT ON UPDATE RESTRICT,
                FOREIGN KEY (PARENT_COUNTRY, PARENT_AREA) REFERENCES STORES (COUNTRY, AREA) ON DELETE SET NULL ON UPDATE RESTRICT
            )
            """.trimIndent(),
        )

        val source = generate(schemas = listOf("PUBLIC", "SALES")).sourceOf("Stores")

        // keys are sorted by name, so the generated (CONSTRAINT_...) names keep their reported order
        assertContains(source, "foreignKey(country to SalesRegions.country, area to SalesRegions.area)")
        assertContains(source, "foreignKey(parentCountry to country, parentArea to area, onDelete = ReferenceOption.SET_NULL)")
        assertFalse(source.contains("name = "), "Generated key names must be omitted: $source")
    }

    @Test
    fun testSelfReferenceToALaterColumnIsDeclaredInTheInitBlock() {
        execute(
            """
            CREATE TABLE NODES (
                PARENT_ID INT NULL,
                ID INT NOT NULL PRIMARY KEY,
                ROOT_ID INT NULL,
                FOREIGN KEY (PARENT_ID) REFERENCES NODES (ID) ON DELETE SET NULL ON UPDATE RESTRICT,
                FOREIGN KEY (ROOT_ID) REFERENCES NODES (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
            )
            """.trimIndent()
        )

        val source = generatedSource(generate().single())

        // the property referencing a later column carries no references(); the key moves to init, which runs after all properties
        assertContains(source, "val parentId: Column<Int?> = integer(\"PARENT_ID\").nullable()")
        assertContains(source, "init { foreignKey(parentId to id, onDelete = ReferenceOption.SET_NULL) }")
        // a self-reference to an earlier column stays on the property
        assertContains(source, "val rootId: Column<Int?> = integer(\"ROOT_ID\").references(id).nullable()")
        assertFalse(messages.any { it.contains("cycle") }, "A self-reference is not a cycle: $messages")
    }

    @Test
    fun testMutualReferencesAreReportedAsACycle() {
        execute(
            "CREATE TABLE USERS (ID INT NOT NULL PRIMARY KEY, LAST_ORDER_ID INT NULL)",
            "CREATE TABLE ORDERS (ID INT NOT NULL PRIMARY KEY, USER_ID INT NOT NULL REFERENCES USERS (ID) ON DELETE RESTRICT ON UPDATE RESTRICT)",
            "ALTER TABLE USERS ADD FOREIGN KEY (LAST_ORDER_ID) REFERENCES ORDERS (ID) ON DELETE SET NULL ON UPDATE RESTRICT",
            "CREATE TABLE NOTES (ID INT NOT NULL PRIMARY KEY, ORDER_ID INT NOT NULL REFERENCES ORDERS (ID) ON DELETE RESTRICT ON UPDATE RESTRICT)",
        )

        val generated = generate()

        // both references are generated, and both objects of the cycle are marked;
        // a table that only points into the cycle is not
        val usersSource = generated.sourceOf("Users")
        val ordersSource = generated.sourceOf("Orders")
        val notesSource = generated.sourceOf("Notes")
        assertContains(
            usersSource,
            "val lastOrderId: Column<Int?> = integer(\"LAST_ORDER_ID\").references(Orders.id, onDelete = ReferenceOption.SET_NULL).nullable()"
        )
        assertContains(ordersSource, "val userId: Column<Int> = integer(\"USER_ID\").references(Users.id)")
        assertContains(usersSource, "TODO: This table and 'ORDERS' reference each other in a cycle")
        assertContains(ordersSource, "TODO: This table and 'USERS' reference each other in a cycle")
        assertFalse(notesSource.contains("cycle"), "A table outside the cycle must not be marked: $notesSource")
        assertEquals(
            1,
            messages.count { it.startsWith("Warning: Tables ") && it.contains("reference each other in a cycle") },
            "One warning per cycle: $messages"
        )
        assertTrue(
            messages.any { it.contains("'ORDERS'") && it.contains("'USERS'") && it.contains("cycle") },
            "The warning names the tables: $messages"
        )
    }

    @Test
    fun testMappedColumnsOnEitherSideOfAKeyAreWarnedAbout() {
        execute(
            "CREATE TABLE USERS (ID INT NOT NULL PRIMARY KEY)",
            "CREATE TABLE ORDERS (ID INT NOT NULL PRIMARY KEY, USER_ID INT NOT NULL REFERENCES USERS (ID) ON DELETE RESTRICT ON UPDATE RESTRICT)",
        )

        val source = generate(
            columnMappings = listOf(ColumnMapping("ORDERS.USER_ID", "uinteger(\"USER_ID\")"))
        ).sourceOf("Orders")

        // the reference is still appended to the mapped code, since it is derived from the database
        assertContains(source, "val userId = uinteger(\"USER_ID\").references(Users.id)")
        assertWarning("Column 'USER_ID' of table 'ORDERS' references USERS.ID and one of the two columns is mapped")
    }
}
