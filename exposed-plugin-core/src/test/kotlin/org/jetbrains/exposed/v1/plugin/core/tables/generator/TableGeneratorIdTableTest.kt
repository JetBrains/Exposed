package org.jetbrains.exposed.v1.plugin.core.tables.generator

import org.jetbrains.exposed.v1.plugin.core.tables.ColumnMapping
import org.jetbrains.exposed.v1.plugin.core.tables.TableGeneratorTestBase
import org.jetbrains.exposed.v1.plugin.core.tables.UuidColumnType
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TableGeneratorIdTableTest : TableGeneratorTestBase() {
    @Test
    fun testActualIdTablesForIdentityAndUuidKeys() {
        execute(
            "CREATE SEQUENCE ORDERS_SEQ",
            "CREATE TABLE USERS (ID INT AUTO_INCREMENT PRIMARY KEY, EMAIL VARCHAR(50) NOT NULL)",
            "CREATE TABLE ORDERS (ID BIGINT DEFAULT NEXT VALUE FOR ORDERS_SEQ NOT NULL PRIMARY KEY, TOTAL DECIMAL(10, 2) NOT NULL)",
            "CREATE TABLE TOKENS (ID UUID NOT NULL PRIMARY KEY, NOTE VARCHAR(10) NULL)",
            "CREATE TABLE NAMED (ID INT AUTO_INCREMENT, CONSTRAINT PK_NAMED_CUSTOM PRIMARY KEY (ID))",
        )

        val generated = generate(generateIdTables = true)

        val usersSource = generated.sourceOf("Users")
        assertContains(usersSource, "object Users : IntIdTable(\"USERS\", \"ID\") {")
        assertContains(usersSource, "val email: Column<String> = varchar(\"EMAIL\", 50)")
        assertFalse(usersSource.contains("val id"), "The subclass declares the key column: $usersSource")
        assertFalse(usersSource.contains("primaryKey"), "The subclass declares the primary key: $usersSource")
        assertContains(usersSource, "import org.jetbrains.exposed.v1.core.dao.id.IntIdTable")
        assertContains(generated.sourceOf("Orders"), "object Orders : LongIdTable(\"ORDERS\", \"ID\", \"ORDERS_SEQ\") {")
        assertContains(generated.sourceOf("Tokens"), "object Tokens : UuidTable(\"TOKENS\", \"ID\") {")

        // a custom primary key name cannot be kept on an actual subclass, so it is reported;
        // and with the key as its only column, the object has no body block
        val namedSource = generated.sourceOf("Named")
        assertContains(namedSource, "object Named : IntIdTable(\"NAMED\", \"ID\")")
        assertFalse(namedSource.contains("{"), "An object with only its key column has no body: $namedSource")
        assertLogged("Table 'NAMED' is generated as a IntIdTable, whose primary key is unnamed; the key name 'PK_NAMED_CUSTOM' is not kept")

        clearGeneratedFiles()
        val javaTokens = generate(generateIdTables = true, uuidColumnType = UuidColumnType.JAVA).sourceOf("Tokens")
        assertContains(javaTokens, "object Tokens : UUIDTable(\"TOKENS\", \"ID\") {")
        assertContains(javaTokens, "import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable")
    }

    @Test
    fun testGenericIdTablesForOtherSingleColumnKeys() {
        execute(
            "CREATE TABLE USERS (ID INT AUTO_INCREMENT PRIMARY KEY)",
            "CREATE TABLE PRODUCTS (SKU VARCHAR(32) NOT NULL PRIMARY KEY, NAME VARCHAR(50) NOT NULL)",
            "CREATE TABLE COUNTERS (ID INT NOT NULL PRIMARY KEY, HITS INT NOT NULL)",
            "CREATE TABLE PROFILES (USER_ID INT NOT NULL PRIMARY KEY REFERENCES USERS (ID) ON DELETE CASCADE ON UPDATE RESTRICT, BIO VARCHAR(200) NULL)",
            "CREATE TABLE AUDITED (ID UUID DEFAULT RANDOM_UUID() NOT NULL PRIMARY KEY)",
            "CREATE SEQUENCE TICKETS_SEQ START WITH 1000",
            "CREATE TABLE TICKETS (ID BIGINT DEFAULT NEXT VALUE FOR TICKETS_SEQ NOT NULL PRIMARY KEY, NOTE VARCHAR(10) NULL)",
        )

        val generated = generate(generateIdTables = true)

        val productsSource = generated.sourceOf("Products")
        assertContains(productsSource, "object Products : IdTable<String>(\"PRODUCTS\") {")
        assertContains(productsSource, "override val id: Column<EntityID<String>> = varchar(\"SKU\", 32).entityId()")
        assertContains(productsSource, "val name: Column<String> = varchar(\"NAME\", 50)")
        assertContains(productsSource, "override val primaryKey: Table.PrimaryKey = PrimaryKey(id)")
        assertContains(productsSource, "import org.jetbrains.exposed.v1.core.dao.id.EntityID")
        assertContains(productsSource, "import org.jetbrains.exposed.v1.core.dao.id.IdTable")

        // an integer key without identity is not an IntIdTable, because it would declare autoIncrement
        val countersSource = generated.sourceOf("Counters")
        assertContains(countersSource, "object Counters : IdTable<Int>(\"COUNTERS\") {")
        assertContains(countersSource, "override val id: Column<EntityID<Int>> = integer(\"ID\").entityId()")

        // a key that is also a foreign key to an IdTable is declared with reference(), which already yields an EntityID column
        val profilesSource = generated.sourceOf("Profiles")
        assertContains(profilesSource, "object Profiles : IdTable<Int>(\"PROFILES\") {")
        assertContains(
            profilesSource,
            "override val id: Column<EntityID<Int>> = reference(\"USER_ID\", Users, onDelete = ReferenceOption.CASCADE)"
        )
        assertFalse(profilesSource.contains("entityId()"), "A reference() column is an EntityID column already: $profilesSource")

        // a key with anything extra, like a database default, keeps the generic form
        val auditedSource = generated.sourceOf("Audited")
        assertContains(auditedSource, "object Audited : IdTable<Uuid>(\"AUDITED\") {")
        assertContains(auditedSource, "override val id: Column<EntityID<Uuid>> = uuid(\"ID\").databaseGenerated().entityId()")
        assertTrue(
            messages.any { it.contains("'ID' of table 'AUDITED' has the default RANDOM_UUID()") },
            "Expected the default to be reported: $messages"
        )

        // a sequence with parameters of its own cannot be passed to IdTable, which takes a sequence name only
        // so the key stays in the body
        val ticketsSource = generated.sourceOf("Tickets")
        assertContains(ticketsSource, "object Tickets : IdTable<Long>(\"TICKETS\") {")
        assertContains(
            ticketsSource,
            "override val id: Column<EntityID<Long>> = long(\"ID\").autoIncrement(Sequence(\"TICKETS_SEQ\", startWith = 1000)).entityId()"
        )
    }

    @Test
    fun testCompositeIdTable() {
        execute(
            "CREATE TABLE ORDER_ITEMS (ORDER_ID INT NOT NULL, ITEM_NO INT NOT NULL, QTY INT NOT NULL, PRIMARY KEY (ORDER_ID, ITEM_NO))",
        )

        val source = generatedSource(generate(generateIdTables = true).single())

        assertContains(source, "object OrderItems : CompositeIdTable(\"ORDER_ITEMS\") {")
        assertContains(source, "val orderId: Column<EntityID<Int>> = integer(\"ORDER_ID\").entityId()")
        assertContains(source, "val itemNo: Column<EntityID<Int>> = integer(\"ITEM_NO\").entityId()")
        assertContains(source, "val qty: Column<Int> = integer(\"QTY\")")
        assertContains(source, "override val primaryKey: Table.PrimaryKey = PrimaryKey(orderId, itemNo)")
        assertContains(source, "import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable")
    }

    @Test
    fun testPlainTableFallbacksAndMappedKeys() {
        execute(
            "CREATE TABLE LOGS (MESSAGE VARCHAR(100) NOT NULL)",
            "CREATE TABLE BROKEN (SPAN INTERVAL DAY NOT NULL PRIMARY KEY, NOTE VARCHAR(10) NULL)",
            "CREATE TABLE CODES (CODE VARCHAR(5) NOT NULL PRIMARY KEY)",
            "CREATE TABLE OPAQUE (CODE VARCHAR(5) NOT NULL PRIMARY KEY)",
        )

        val generated = generate(
            generateIdTables = true,
            columnMappings = listOf(
                // a mapped key whose function the generator is aware of can be used to type the IdTable
                ColumnMapping("CODES.CODE", "varchar(\"CODE\", 5)"),
                // a mapped key whose Kotlin type cannot be detected must use a plain Table
                ColumnMapping("OPAQUE.CODE", "registerColumn(\"CODE\", CodeType())", listOf("com.example.CodeType")),
            ),
        )

        assertContains(generated.sourceOf("Logs"), "object Logs : Table(\"LOGS\") {")
        val brokenSource = generated.sourceOf("Broken")
        assertContains(brokenSource, "object Broken : Table(\"BROKEN\") {")
        assertContains(brokenSource, "val note: Column<String?> = varchar(\"NOTE\", 10).nullable()")
        assertFalse(brokenSource.contains("primaryKey"), "An ungenerated key column cannot be part of a primary key: $brokenSource")
        assertWarning("Table 'BROKEN' is generated as a plain Table rather than an IdTable, because its primary key includes 'SPAN'")
        val codesSource = generated.sourceOf("Codes")
        assertContains(codesSource, "object Codes : IdTable<String>(\"CODES\") {")
        assertContains(codesSource, "override val id = varchar(\"CODE\", 5).entityId()")
        assertContains(codesSource, "override val primaryKey: Table.PrimaryKey = PrimaryKey(id)")
        val opaqueSource = generated.sourceOf("Opaque")
        assertContains(opaqueSource, "object Opaque : Table(\"OPAQUE\") {")
        assertContains(opaqueSource, "val code = registerColumn(\"CODE\", CodeType())")
        val expectedWarning = "Table 'OPAQUE' is generated as a plain Table rather than an IdTable, " +
            "because the Kotlin type of its mapped key column 'CODE'"
        assertTrue(
            messages.any { it.contains(expectedWarning) },
            "Expected a warning about the untyped mapped key: $messages"
        )
    }

    @Test
    fun testReferencesToIdTablesUseReferenceAndOptReference() {
        execute(
            "CREATE TABLE USERS (ID INT AUTO_INCREMENT PRIMARY KEY, EMAIL VARCHAR(50) NOT NULL UNIQUE)",
            """
            CREATE TABLE ORDERS (
                ID INT AUTO_INCREMENT PRIMARY KEY,
                USER_ID INT NOT NULL REFERENCES USERS (ID) ON DELETE CASCADE ON UPDATE RESTRICT,
                REVIEWER_ID INT NULL REFERENCES USERS (ID) ON DELETE RESTRICT ON UPDATE RESTRICT,
                OWNER_EMAIL VARCHAR(50) NULL REFERENCES USERS (EMAIL) ON DELETE RESTRICT ON UPDATE RESTRICT,
                PARENT_ID INT NULL REFERENCES ORDERS (ID) ON DELETE SET NULL ON UPDATE RESTRICT
            )
            """.trimIndent(),
            """
            CREATE TABLE ORDER_ITEMS (
                ORDER_ID INT NOT NULL REFERENCES ORDERS (ID) ON DELETE RESTRICT ON UPDATE RESTRICT,
                ITEM_NO INT NOT NULL,
                PRIMARY KEY (ORDER_ID, ITEM_NO)
            )
            """.trimIndent(),
            "CREATE TABLE ITEM_NOTES (ID INT AUTO_INCREMENT PRIMARY KEY, ORDER_ID INT NOT NULL, ITEM_NO INT NOT NULL, " +
                "FOREIGN KEY (ORDER_ID, ITEM_NO) REFERENCES ORDER_ITEMS (ORDER_ID, ITEM_NO) ON DELETE RESTRICT ON UPDATE RESTRICT)",
        )

        val generated = generate(generateIdTables = true)

        val ordersSource = generated.sourceOf("Orders")
        // a key to an IdTable's id replaces the factory call
        assertContains(ordersSource, "val userId: Column<EntityID<Int>> = reference(\"USER_ID\", Users, onDelete = ReferenceOption.CASCADE)")
        // a nullable key uses optReference()
        assertContains(ordersSource, "val reviewerId: Column<EntityID<Int>?> = optReference(\"REVIEWER_ID\", Users)")
        // a key to a column other than the id keeps the references() form
        assertContains(ordersSource, "val ownerEmail: Column<String?> = varchar(\"OWNER_EMAIL\", 50).references(Users.email).nullable()")
        // a self-reference to the subclass-declared id names the object itself
        assertContains(ordersSource, "val parentId: Column<EntityID<Int>?> = optReference(\"PARENT_ID\", Orders, onDelete = ReferenceOption.SET_NULL)")
        assertContains(ordersSource, "import org.jetbrains.exposed.v1.core.dao.id.EntityID")

        // a composite key column that references an IdTable's id is a reference() column
        val itemsSource = generated.sourceOf("OrderItems")
        assertContains(itemsSource, "object OrderItems : CompositeIdTable(\"ORDER_ITEMS\") {")
        assertContains(itemsSource, "val orderId: Column<EntityID<Int>> = reference(\"ORDER_ID\", Orders)")
        assertContains(itemsSource, "val itemNo: Column<EntityID<Int>> = integer(\"ITEM_NO\").entityId()")
        assertContains(itemsSource, "override val primaryKey: Table.PrimaryKey = PrimaryKey(orderId, itemNo)")

        // a composite key to a CompositeIdTable stays in the pair form
        val notesSource = generated.sourceOf("ItemNotes")
        assertContains(notesSource, "init { foreignKey(orderId to OrderItems.orderId, itemNo to OrderItems.itemNo) }")
    }

    @Test
    fun testOptionOffKeepsPlainTables() {
        execute("CREATE TABLE USERS (ID INT AUTO_INCREMENT PRIMARY KEY, EMAIL VARCHAR(50) NOT NULL)")

        val source = generatedSource(generate().single())

        assertContains(source, "object Users : Table(\"USERS\") {")
        assertContains(source, "val id: Column<Int> = integer(\"ID\").autoIncrement()")
        assertContains(source, "override val primaryKey: Table.PrimaryKey = PrimaryKey(id)")
        assertFalse(source.contains("IdTable"), "No IdTable expected with the option off: $source")
    }
}
