package org.jetbrains.exposed.v1.plugin.core.tables.generator

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.datetime.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.exists
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.json.json
import org.jetbrains.exposed.v1.json.jsonb
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import org.jetbrains.exposed.v1.plugin.core.tables.ColumnMapping
import org.jetbrains.exposed.v1.plugin.core.tables.TableGeneratorTestBase
import org.jetbrains.exposed.v1.plugin.core.tables.UuidColumnType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

/**
 * Round-trip tests: tables are created on H2 from manual definitions. The generator then reverse-engineers that schema,
 * and the generated definitions are checked to resolve the existing tables without requiring any migration statements.
 *
 * The generated code cannot be compiled inside the test, so each generated object is mirrored by an equivalent
 * `object` declared below. The assertions on the generated source and the mirrors must be kept in sync.
 *
 * The generator maps on the declared column type that H2 reports through `SHOW COLUMNS`.
 * The one function that cannot be recovered is asserted explicitly:
 *  - `javaUUID()` and `uuid()` both emit `UUID`, so both are regenerated according to user-defined configuration.
 */
@OptIn(ExperimentalUuidApi::class)
class TableGeneratorRoundTripTest : TableGeneratorTestBase() {
    private lateinit var database: Database

    // --- START of as many table object permutations as the plugin supports

    private object SourceTypes : Table("SOURCE_TYPES") {
        val tiny = byte("TINY")
        val small = short("SMALL")
        val normal = integer("NORMAL")
        val big = long("BIG")
        val exact = decimal("EXACT", 10, 2)
        val single = float("SINGLE")
        val dbl = double("DBL")
        val flag = bool("FLAG")
        val fixed = char("FIXED", 3)
        val letter = char("LETTER")
        val shortText = varchar("SHORT_TEXT", 255)
        val longText = text("LONG_TEXT")
        val raw = binary("RAW", 16)
        val rawUnbounded = binary("RAW_UNBOUNDED")
        val largeRaw = blob("LARGE_RAW")
        val uniqueId = uuid("UNIQUE_ID")
        val javaId = javaUUID("JAVA_ID")
        val optional = integer("OPTIONAL").nullable()

        override val primaryKey = PrimaryKey(normal)
    }

    /** DDL-equivalent mirror of what the generator writes for [SourceTypes] with the default (Kotlin) UUID mapping. */
    private object GeneratedSourceTypes : Table("SOURCE_TYPES") {
        val tiny = byte("TINY")
        val small = short("SMALL")
        val normal = integer("NORMAL")
        val big = long("BIG")
        val exact = decimal("EXACT", 10, 2)
        val single = float("SINGLE")
        val dbl = double("DBL")
        val flag = bool("FLAG")
        val fixed = char("FIXED", 3)
        val letter = char("LETTER")
        val shortText = varchar("SHORT_TEXT", 255)
        val longText = text("LONG_TEXT")
        val raw = binary("RAW", 16)
        val rawUnbounded = binary("RAW_UNBOUNDED")
        val largeRaw = blob("LARGE_RAW")
        val uniqueId = uuid("UNIQUE_ID")
        val javaId = uuid("JAVA_ID")
        val optional = integer("OPTIONAL").nullable()

        override val primaryKey: PrimaryKey = PrimaryKey(normal)
    }

    /** DDL-equivalent mirror of what the generator writes for [SourceTypes] with the Java UUID mapping. */
    private object GeneratedSourceTypesJavaUuid : Table("SOURCE_TYPES") {
        val tiny = byte("TINY")
        val small = short("SMALL")
        val normal = integer("NORMAL")
        val big = long("BIG")
        val exact = decimal("EXACT", 10, 2)
        val single = float("SINGLE")
        val dbl = double("DBL")
        val flag = bool("FLAG")
        val fixed = char("FIXED", 3)
        val letter = char("LETTER")
        val shortText = varchar("SHORT_TEXT", 255)
        val longText = text("LONG_TEXT")
        val raw = binary("RAW", 16)
        val rawUnbounded = binary("RAW_UNBOUNDED")
        val largeRaw = blob("LARGE_RAW")
        val uniqueId = javaUUID("UNIQUE_ID")
        val javaId = javaUUID("JAVA_ID")
        val optional = integer("OPTIONAL").nullable()

        override val primaryKey: PrimaryKey = PrimaryKey(normal)
    }

    private object SourceIndexed : Table("SOURCE_INDEXED") {
        val id = integer("ID").autoIncrement()
        val category = varchar("CATEGORY", 32).index()
        val name = varchar("NAME", 64).index("IDX_SOURCE_INDEXED_NAME")
        val rank = short("RANK").nullable()
        val code = varchar("CODE", 16).uniqueIndex()

        override val primaryKey = PrimaryKey(id, name = "PK_SOURCE_INDEXED_CUSTOM")

        init {
            index(false, category, name)
            index("IDX_SOURCE_INDEXED_CATEGORY_RANK", false, category, rank)
            uniqueIndex(name, rank)
        }
    }

    /** DDL-equivalent mirror of what the generator writes for [SourceIndexed]. */
    private object GeneratedSourceIndexed : Table("SOURCE_INDEXED") {
        val id = integer("ID").autoIncrement()
        val category = varchar("CATEGORY", 32).index()
        val name = varchar("NAME", 64).index("IDX_SOURCE_INDEXED_NAME")
        val rank = short("RANK").nullable()
        val code = varchar("CODE", 16).uniqueIndex()

        override val primaryKey: PrimaryKey = PrimaryKey(id, name = "PK_SOURCE_INDEXED_CUSTOM")

        init {
            index(false, category, name)
            index("IDX_SOURCE_INDEXED_CATEGORY_RANK", false, category, rank)
            uniqueIndex(name, rank)
        }
    }

    /** DDL-equivalent mirror of the `Users` object the generator writes. */
    private object GeneratedUsersMirror : Table("USERS") {
        val id: Column<Int> = integer("ID").autoIncrement()
        val email: Column<String> = varchar("EMAIL", 320)
        val age: Column<Int?> = integer("AGE").nullable()

        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    /**
     * DDL-equivalent mirror of the `Reserved` object the generator writes.
     * The generator names these properties `object` and `fun` (escaped with backticks).
     * `object` is an SQL keyword, which Exposed quotes itself, so its literal stays bare.
     */
    private object GeneratedReservedMirror : Table("\"reserved\"") {
        val objectColumn: Column<Int> = integer("object")
        val funColumn: Column<String?> = varchar("\"fun\"", 10).nullable()
    }

    private object SourceComposite : Table("SOURCE_COMPOSITE") {
        val qty = integer("QTY")
        val itemId = integer("ITEM_ID")
        val orderId = long("ORDER_ID")

        // KEEP - composite primary key purposefully declared in a different order than its columns
        override val primaryKey = PrimaryKey(orderId, itemId)
    }

    /** DDL-equivalent mirror of what the generator writes for [SourceComposite]. */
    private object GeneratedSourceComposite : Table("SOURCE_COMPOSITE") {
        val qty = integer("QTY")
        val itemId = integer("ITEM_ID")
        val orderId = long("ORDER_ID")

        override val primaryKey: PrimaryKey = PrimaryKey(orderId, itemId)
    }

    private object SourceChecked : Table("SOURCE_CHECKED") {
        val id = integer("ID")
        val age = integer("AGE").check("SOURCE_CHECKED_AGE") { it greaterEq 0 }
        val name = varchar("NAME", 50)

        override val primaryKey = PrimaryKey(id)

        init {
            check("SOURCE_CHECKED_AGE_NAME") { (age less 150) and (name neq "") }
        }
    }

    /** DDL-equivalent mirror of what the generator writes for [SourceChecked]. */
    private object GeneratedSourceChecked : Table("SOURCE_CHECKED") {
        val id = integer("ID")
        val age = integer("AGE").check("SOURCE_CHECKED_AGE") { it greaterEq 0 }
        val name = varchar("NAME", 50)

        override val primaryKey: PrimaryKey = PrimaryKey(id)

        init {
            check("SOURCE_CHECKED_AGE_NAME") { (age less 150) and (name neq "") }
        }
    }

    private object SourceSequenced : Table("SOURCE_SEQUENCED") {
        val id = long("ID").autoIncrement("SOURCE_SEQUENCED_SEQ")
        val note = varchar("NOTE", 10).nullable()

        override val primaryKey = PrimaryKey(id)
    }

    /** DDL-equivalent mirror of what the generator writes for [SourceSequenced]. */
    private object GeneratedSourceSequenced : Table("SOURCE_SEQUENCED") {
        val id = long("ID")
        val note = varchar("NOTE", 10).nullable()

        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    /**
     * DDL-equivalent mirror of what the generator writes for a key backed by a sequence with parameters of its own, see
     * [testSequenceParametersRoundTrip].
     */
    private object GeneratedTickets : Table("TICKETS") {
        val id = long("ID").autoIncrement(
            Sequence("TICKETS_SEQ", startWith = 100, incrementBy = 5, minValue = 100, maxValue = 100000, cycle = true, cache = 10)
        )
        val note = varchar("NOTE", 10).nullable()

        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    /** DDL-equivalent mirror of a key backed by a sequence with default parameters, for comparison with [GeneratedTickets]. */
    private object GeneratedPlainTickets : Table("PLAIN_TICKETS") {
        val id = long("ID").autoIncrement("PLAIN_SEQ")

        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    private object SourceCommented : Table("SOURCE_COMMENTED") {
        val id = integer("ID").comment("Identifier")
        val note = varchar("NOTE", 100).comment("Free text with 'quotes'").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    /** DDL-equivalent mirror of what the generator writes for [SourceCommented]. */
    private object GeneratedSourceCommented : Table("SOURCE_COMMENTED") {
        val id = integer("ID").comment("Identifier")
        val note = varchar("NOTE", 100).comment("Free text with 'quotes'").nullable()

        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    private object SourceInSales : Table("SALES.ORDERS") {
        val id = integer("ID")
        val total = decimal("TOTAL", 10, 2)

        override val primaryKey = PrimaryKey(id)
    }

    /** DDL-equivalent mirror of what the generator writes for [SourceInSales] when the `SALES` schema is searched. */
    private object GeneratedSalesOrders : Table("SALES.ORDERS") {
        val id = integer("ID")
        val total = decimal("TOTAL", 10, 2)

        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    /**
     * DDL-equivalent mirror of what the generator writes for a table with a computed column, see
     * [testGeneratedColumnRoundTrip].
     */
    private object GeneratedLineItems : Table("LINE_ITEMS") {
        val id = integer("ID")
        val price = decimal("PRICE", 10, 2)
        val qty = integer("QTY")
        val total = decimal("TOTAL", 12, 2).databaseGenerated().nullable()

        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    private object SourceTemporal : Table("SOURCE_TEMPORAL") {
        val id = integer("ID")
        val day = date("DAY")
        val atTime = time("AT_TIME")
        val createdAt = datetime("CREATED_AT")
        val recordedAt = timestamp("RECORDED_AT")
        val publishedAt = timestampWithTimeZone("PUBLISHED_AT").nullable()
        val elapsed = duration("ELAPSED")

        override val primaryKey = PrimaryKey(id)
    }

    /**
     * DDL-equivalent mirror of what the generator writes for [SourceTemporal]: `timestamp()` comes back as `datetime()`
     * (both declare `TIMESTAMP(9)` on H2) and `duration()` as `long()` (it is stored as `BIGINT`).
     */
    private object GeneratedSourceTemporal : Table("SOURCE_TEMPORAL") {
        val id = integer("ID")
        val day = date("DAY")
        val atTime = time("AT_TIME")
        val createdAt = datetime("CREATED_AT")
        val recordedAt = datetime("RECORDED_AT")
        val publishedAt = timestampWithTimeZone("PUBLISHED_AT").nullable()
        val elapsed = long("ELAPSED")

        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    private object SourceJson : Table("SOURCE_JSON") {
        val id = integer("ID")
        val document = json<String>("DOCUMENT", { it }, { it })
        val binaryDocument = jsonb<String>("BINARY_DOCUMENT", { it }, { it }).nullable()

        override val primaryKey = PrimaryKey(id)
    }

    /**
     * DDL-equivalent mirror of what the generator writes for [SourceJson]: H2 has a single JSON format, so `jsonb()`
     * declares the same `JSON` as `json()` and comes back as `json()`.
     */
    private object GeneratedSourceJson : Table("SOURCE_JSON") {
        val id = integer("ID")
        val document = json<String>("DOCUMENT", { it }, { it })
        val binaryDocument = json<String>("BINARY_DOCUMENT", { it }, { it }).nullable()

        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    private object SourceArrays : Table("SOURCE_ARRAYS") {
        val id = integer("ID")
        val ids = array<Int>("IDS", 10)
        val tags = array("TAGS", VarCharColumnType(32))
        val prices = array("PRICES", DecimalColumnType(10, 2))
        val days = array("DAYS", KotlinLocalDateColumnType()).nullable()

        override val primaryKey = PrimaryKey(id)
    }

    /**
     * DDL-equivalent mirror of what the generator writes for [SourceArrays]: always the overload with an explicit
     * element column type, since the reified form resolves element types by reflection rather than from the database.
     */
    private object GeneratedSourceArrays : Table("SOURCE_ARRAYS") {
        val id = integer("ID")
        val ids = array("IDS", IntegerColumnType(), 10)
        val tags = array("TAGS", VarCharColumnType(32))
        val prices = array("PRICES", DecimalColumnType(10, 2))
        val days = array("DAYS", KotlinLocalDateColumnType()).nullable()

        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    private enum class Foo { Bar, Baz }

    private object SourceEnumerated : Table("SOURCE_ENUMERATED") {
        val id = integer("ID")
        val status = customEnumeration("STATUS", "ENUM('Bar', 'Baz')", { Foo.valueOf(it as String) }, { it.name })

        override val primaryKey = PrimaryKey(id)
    }

    /** DDL-equivalent mirror of what the generator writes for [SourceEnumerated]: the literal is passed back verbatim. */
    private object GeneratedSourceEnumerated : Table("SOURCE_ENUMERATED") {
        internal enum class Status { Bar, Baz }

        val id = integer("ID")
        val status = customEnumeration("STATUS", "ENUM('Bar', 'Baz')", { Status.valueOf(it as String) }, { it.name })

        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    /**
     * This manual definition acts as its own DDL-equivalent mirror.
     */
    private object SourceDefaults : Table("SOURCE_DEFAULTS") {
        val id = integer("ID")
        val retries = integer("RETRIES").default(3)
        val offsetMs = long("OFFSET_MS").default(-500L)
        val ratio = double("RATIO").default(0.75)
        val price = decimal("PRICE", 10, 2).default(BigDecimal("12.50"))
        val active = bool("ACTIVE").default(true)
        val label = varchar("LABEL", 20).default("pending")
        val note = varchar("NOTE", 10).default("").nullable()
        val createdAt = datetime("CREATED_AT").defaultExpression(CurrentDateTime)
        val startsOn = date("STARTS_ON").defaultExpression(CurrentDate)

        override val primaryKey = PrimaryKey(id)
    }

    private object SourceUsers : Table("SOURCE_USERS") {
        val id = integer("ID")
        val email = varchar("EMAIL", 50).uniqueIndex()

        override val primaryKey = PrimaryKey(id)
    }

    private object SourceOrders : Table("SOURCE_ORDERS") {
        val id = integer("ID")
        val userId = integer("USER_ID").references(SourceUsers.id)
        val ownerEmail = varchar("OWNER_EMAIL", 50).references(SourceUsers.email, onDelete = ReferenceOption.CASCADE).nullable()
        val parentId = integer("PARENT_ID").references(id, onDelete = ReferenceOption.SET_NULL).nullable()

        override val primaryKey = PrimaryKey(id)
    }

    /** DDL-equivalent mirror of what the generator writes for [SourceUsers]. */
    private object GeneratedSourceUsers : Table("SOURCE_USERS") {
        val id = integer("ID")
        val email = varchar("EMAIL", 50).uniqueIndex()

        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    /** DDL-equivalent mirror of what the generator writes for [SourceOrders]: default names and default rules are omitted. */
    private object GeneratedSourceOrders : Table("SOURCE_ORDERS") {
        val id = integer("ID")
        val userId = integer("USER_ID").references(GeneratedSourceUsers.id)
        val ownerEmail = varchar("OWNER_EMAIL", 50).references(GeneratedSourceUsers.email, onDelete = ReferenceOption.CASCADE).nullable()
        val parentId = integer("PARENT_ID").references(id, onDelete = ReferenceOption.SET_NULL).nullable()

        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    private object SourceParts : Table("SOURCE_PARTS") {
        val maker = varchar("MAKER", 10)
        val code = varchar("CODE", 10)

        override val primaryKey = PrimaryKey(maker, code)
    }

    private object SourceStock : Table("SOURCE_STOCK") {
        val id = integer("ID")
        val partMaker = varchar("PART_MAKER", 10)
        val partCode = varchar("PART_CODE", 10)

        override val primaryKey = PrimaryKey(id)

        init {
            foreignKey(partMaker to SourceParts.maker, partCode to SourceParts.code, onDelete = ReferenceOption.CASCADE)
        }
    }

    /** DDL-equivalent mirror of what the generator writes for [SourceParts]. */
    private object GeneratedSourceParts : Table("SOURCE_PARTS") {
        val maker = varchar("MAKER", 10)
        val code = varchar("CODE", 10)

        override val primaryKey: PrimaryKey = PrimaryKey(maker, code)
    }

    /** DDL-equivalent mirror of what the generator writes for [SourceStock]: the default key name is omitted. */
    private object GeneratedSourceStock : Table("SOURCE_STOCK") {
        val id = integer("ID")
        val partMaker = varchar("PART_MAKER", 10)
        val partCode = varchar("PART_CODE", 10)

        override val primaryKey: PrimaryKey = PrimaryKey(id)

        init {
            foreignKey(partMaker to GeneratedSourceParts.maker, partCode to GeneratedSourceParts.code, onDelete = ReferenceOption.CASCADE)
        }
    }

    /**
     * DDL-equivalent mirror of what the generator writes for a table whose self-reference points at a later column,
     * see [testSelfReferenceToLaterColumnRoundTrip]. Such a table cannot be manually defined with `references()` on the
     * property, so the test creates it with raw DDL; the init block form is the only one that initializes.
     */
    private object GeneratedNodes : Table("NODES") {
        val parentId = integer("PARENT_ID").nullable()
        val id = integer("ID")

        override val primaryKey: PrimaryKey = PrimaryKey(id)

        init {
            foreignKey(parentId to id, onDelete = ReferenceOption.SET_NULL)
        }
    }

    /** Manual IdTable forms, see [testIdTablesRoundTrip]; each is its own mirror of what the generator writes. */
    private object SourceAccounts : IntIdTable("SOURCE_ACCOUNTS", "ID") {
        val email = varchar("EMAIL", 50)
    }

    private object SourceSessions : UuidTable("SOURCE_SESSIONS", "ID") {
        val account = reference("ACCOUNT_ID", SourceAccounts, onDelete = ReferenceOption.CASCADE)
        val previous = optReference("PREVIOUS_ID", SourceSessions)
    }

    private object SourceSkus : IdTable<String>("SOURCE_SKUS") {
        override val id: Column<EntityID<String>> = varchar("SKU", 32).entityId()
        val label = varchar("LABEL", 50)

        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    private object SourceLines : CompositeIdTable("SOURCE_LINES") {
        val session = reference("SESSION_ID", SourceSessions)
        val lineNo = integer("LINE_NO").entityId()
        val sku = reference("SKU", SourceSkus)

        override val primaryKey: PrimaryKey = PrimaryKey(session, lineNo)
    }

    // --- END of as many table object permutations as the plugin supports

    @BeforeEach
    fun setup() {
        database = Database.connect(url = databaseUrl, user = "", password = "")
        transaction(database) {
            SchemaUtils.create(SourceTypes, SourceIndexed, SourceComposite, SourceChecked)
        }
    }

    @AfterEach
    fun closeDatabase() {
        TransactionManager.closeAndUnregister(database)
    }

    @Test
    fun testExposedCreatedSchemaRoundTripsWithoutMigrationStatements() {
        val generated = generate()

        val typesSource = generated.sourceOf("SourceTypes")
        assertContains(typesSource, "object SourceTypes : Table(\"SOURCE_TYPES\") {")
        assertContains(typesSource, "val tiny: Column<Byte> = byte(\"TINY\")")
        assertContains(typesSource, "val small: Column<Short> = short(\"SMALL\")")
        assertContains(typesSource, "val normal: Column<Int> = integer(\"NORMAL\")")
        assertContains(typesSource, "val big: Column<Long> = long(\"BIG\")")
        assertContains(typesSource, "val exact: Column<BigDecimal> = decimal(\"EXACT\", 10, 2)")
        // float() is declared as FLOAT, which H2 reports back as FLOAT even though it is stored with double precision
        assertContains(typesSource, "val single: Column<Float> = float(\"SINGLE\")")
        assertContains(typesSource, "val dbl: Column<Double> = double(\"DBL\")")
        assertContains(typesSource, "val flag: Column<Boolean> = bool(\"FLAG\")")
        assertContains(typesSource, "val fixed: Column<String> = char(\"FIXED\", 3)")
        // char() without a length is declared as a bare CHAR, which H2 reports back as CHARACTER without a length
        assertContains(typesSource, "val letter: Column<Char> = char(\"LETTER\")")
        assertContains(typesSource, "val shortText: Column<String> = varchar(\"SHORT_TEXT\", 255)")
        assertContains(typesSource, "val longText: Column<String> = text(\"LONG_TEXT\")")
        assertContains(typesSource, "val raw: Column<ByteArray> = binary(\"RAW\", 16)")
        assertContains(typesSource, "val rawUnbounded: Column<ByteArray> = binary(\"RAW_UNBOUNDED\")")
        assertContains(typesSource, "val largeRaw: Column<ExposedBlob> = blob(\"LARGE_RAW\")")
        assertContains(typesSource, "val uniqueId: Column<Uuid> = uuid(\"UNIQUE_ID\")")
        // javaUUID() and uuid() are indistinguishable in the database, so the configured mapping wins
        assertContains(typesSource, "val javaId: Column<Uuid> = uuid(\"JAVA_ID\")")
        assertContains(typesSource, "val optional: Column<Int?> = integer(\"OPTIONAL\").nullable()")
        assertContains(typesSource, "override val primaryKey: Table.PrimaryKey = PrimaryKey(normal)")

        val indexedSource = generated.sourceOf("SourceIndexed")
        assertContains(indexedSource, "object SourceIndexed : Table(\"SOURCE_INDEXED\") {")
        assertContains(indexedSource, "val id: Column<Int> = integer(\"ID\").autoIncrement()")
        // an index named by Exposed by default is regenerated without a custom name
        assertContains(indexedSource, "val category: Column<String> = varchar(\"CATEGORY\", 32).index()")
        // a custom index name is preserved, since Exposed's migration utilities compare index names
        assertContains(indexedSource, "val name: Column<String> = varchar(\"NAME\", 64).index(\"IDX_SOURCE_INDEXED_NAME\")")
        assertContains(indexedSource, "val rank: Column<Short?> = short(\"RANK\").nullable()")
        // a unique constraint that Exposed named by default is read back through its constraint name, so the name is omitted again
        assertContains(indexedSource, "val code: Column<String> = varchar(\"CODE\", 16).uniqueIndex()")
        // a custom primary key name is preserved; H2-generated and Exposed-default names are not
        assertContains(indexedSource, "override val primaryKey: Table.PrimaryKey = PrimaryKey(id, name = \"PK_SOURCE_INDEXED_CUSTOM\")")
        // indices in the init block are sorted by name: the custom-named one (IDX_...) precedes the default-named ones (SOURCE_INDEXED_...)
        assertContains(
            indexedSource,
            "init { index(\"IDX_SOURCE_INDEXED_CATEGORY_RANK\", false, category, rank) index(false, category, name) uniqueIndex(name, rank) }"
        )
        assertFalse(indexedSource.contains("_INDEX_"), "The name of a backing index must not leak into the definition: $indexedSource")

        val compositeSource = generated.sourceOf("SourceComposite")
        assertContains(compositeSource, "object SourceComposite : Table(\"SOURCE_COMPOSITE\") {")
        // the key columns are passed as varargs in key order, not in column order, and the key's backing index is not generated
        assertContains(compositeSource, "override val primaryKey: Table.PrimaryKey = PrimaryKey(orderId, itemId)")
        assertFalse(compositeSource.contains("uniqueIndex"), "Primary key backing index must not be generated: $compositeSource")
        assertFalse(compositeSource.contains("init {"), "No initializer block expected: $compositeSource")

        assertNoMigrationRequired(GeneratedSourceTypes, GeneratedSourceIndexed, GeneratedSourceComposite)
    }

    @Test
    fun testGeneratedIdentifiersResolveExistingTablesWithoutMigrationStatements() {
        execute(
            """
            CREATE TABLE USERS (
                ID INT AUTO_INCREMENT PRIMARY KEY,
                EMAIL VARCHAR(320) NOT NULL,
                AGE INT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE "reserved" (
                "object" INT NOT NULL,
                "fun" VARCHAR(10) NULL
            )
            """.trimIndent(),
        )

        val generated = generate()
        val usersSource = generated.sourceOf("Users")
        val reservedSource = generated.sourceOf("Reserved")

        // the mirror objects must be kept identical to what the generator writes
        assertContains(usersSource, "object Users : Table(\"USERS\") {")
        assertContains(usersSource, "val id: Column<Int> = integer(\"ID\").autoIncrement()")
        assertContains(usersSource, "val email: Column<String> = varchar(\"EMAIL\", 320)")
        assertContains(usersSource, "val age: Column<Int?> = integer(\"AGE\").nullable()")
        assertContains(usersSource, "override val primaryKey: Table.PrimaryKey = PrimaryKey(id)")
        assertContains(reservedSource, "object Reserved : Table(\"\\\"reserved\\\"\") {")
        assertContains(reservedSource, "val `object`: Column<Int> = integer(\"object\")")
        assertContains(reservedSource, "val `fun`: Column<String?> = varchar(\"\\\"fun\\\"\", 10).nullable()")

        assertNoMigrationRequired(GeneratedUsersMirror, GeneratedReservedMirror)
    }

    // TODO Fix on exposed-jdbc end by introducing parser that diffs detected CHECK constraints by their conditions
    @Disabled("CHECK constraints are not reverse-engineered yet")
    @Test
    fun testCheckConstraintsRoundTrip() {
        val generated = generate()

        val checkedSource = generated.sourceOf("SourceChecked")
        assertContains(
            checkedSource,
            "val age: Column<Int> = integer(\"AGE\").check(\"SOURCE_CHECKED_AGE\") { it greaterEq 0 }"
        )
        assertContains(checkedSource, "init { check(\"SOURCE_CHECKED_AGE_NAME\") { (age less 150) and (name neq \"\") } }")

        assertNoMigrationRequired(GeneratedSourceChecked)
    }

    // On H2, Exposed's `autoIncrement(sequenceName)` creates a plain column without a default and advances the sequence
    // in each INSERT statement instead, so the database holds no link between the column and the sequence. The generator
    // therefore cannot recover the `autoIncrement(...)` call, but it warns about the sequence being unreferenced,
    // because a definition without the call makes Exposed's migration utilities propose dropping the sequence.
    @Test
    fun testSequenceBackedAutoIncrementCannotBeRecoveredOnH2() {
        transaction(database) {
            SchemaUtils.create(SourceSequenced)
        }

        val generated = generate()

        val sequencedSource = generated.sourceOf("SourceSequenced")
        assertContains(sequencedSource, "val id: Column<Long> = long(\"ID\")")
        assertFalse(sequencedSource.contains("autoIncrement"), "The sequence link is not recoverable from H2 metadata: $sequencedSource")
        assertWarning("Sequence 'SOURCE_SEQUENCED_SEQ' is not used as the default value of any generated column")

        transaction(database) {
            assertTrue(GeneratedSourceSequenced.exists())
            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(GeneratedSourceSequenced, withLogs = false)
            assertTrue(
                statements.any { it.contains("DROP SEQUENCE", ignoreCase = true) && it.contains("SOURCE_SEQUENCED_SEQ", ignoreCase = true) },
                "Expected the unmapped sequence to be proposed for dropping, which is what the warning guards against: $statements"
            )
        }
    }

    // The generated sequence parameters match what Exposed's `Sequence` declares, so the sequences themselves need no
    // migration. The columns keep one difference that is Exposed's, not the generator's: on H2, `autoIncrement(sequence)`
    // declares no column default and advances the sequence in each INSERT, while a column created with
    // `DEFAULT NEXT VALUE FOR` carries that default, so Exposed's default comparison proposes an `ALTER COLUMN` for every
    // sequence-backed column, with or without parameters. The test pins exactly that statement and nothing else.
    @Test
    fun testSequenceParametersRoundTrip() {
        execute(
            "CREATE SEQUENCE TICKETS_SEQ START WITH 100 INCREMENT BY 5 MINVALUE 100 MAXVALUE 100000 CYCLE CACHE 10",
            "CREATE TABLE TICKETS (ID BIGINT DEFAULT NEXT VALUE FOR TICKETS_SEQ NOT NULL PRIMARY KEY, NOTE VARCHAR(10) NULL)",
            "CREATE SEQUENCE PLAIN_SEQ",
            "CREATE TABLE PLAIN_TICKETS (ID BIGINT DEFAULT NEXT VALUE FOR PLAIN_SEQ NOT NULL PRIMARY KEY)",
        )

        val generated = generate()

        assertContains(
            generated.sourceOf("Tickets"),
            "val id: Column<Long> = long(\"ID\").autoIncrement(Sequence(\"TICKETS_SEQ\", startWith = 100, incrementBy = 5, " +
                "minValue = 100, maxValue = 100000, cycle = true, cache = 10))"
        )
        assertContains(generated.sourceOf("PlainTickets"), "val id: Column<Long> = long(\"ID\").autoIncrement(\"PLAIN_SEQ\")")

        transaction(database) {
            assertTrue(
                GeneratedTickets.exists() && GeneratedPlainTickets.exists(),
                "Generated definitions should resolve the existing tables"
            )
            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(GeneratedTickets, GeneratedPlainTickets, withLogs = false)
            assertEquals(
                listOf("ALTER TABLE PLAIN_TICKETS ALTER COLUMN ID BIGINT", "ALTER TABLE TICKETS ALTER COLUMN ID BIGINT"),
                statements.sorted(),
                "Only the column default comparison, which is Exposed's, may differ; the sequences themselves must not: $statements"
            )
        }
    }

    // TODO Investigate why column comments are not supported in core by H2 (only by MySQL modes)
    @Disabled("Column comments are not reverse-engineered until Exposed supports H2 comments outside MySQL/MariaDB mode")
    @Test
    fun testColumnCommentsRoundTrip() {
        transaction(database) {
            SchemaUtils.create(SourceCommented)
        }

        val generated = generate()

        val commentedSource = generated.sourceOf("SourceCommented")
        assertContains(commentedSource, "val id: Column<Int> = integer(\"ID\").comment(\"Identifier\")")
        assertContains(commentedSource, "val note: Column<String?> = varchar(\"NOTE\", 100).comment(\"Free text with 'quotes'\").nullable()")

        assertNoMigrationRequired(GeneratedSourceCommented)
    }

    @Test
    fun testOtherSchemaRoundTrip() {
        transaction(database) {
            SchemaUtils.createSchema(Schema("SALES"))
            SchemaUtils.create(SourceInSales)
        }

        val generated = generate(schemas = listOf("SALES"))

        // only the SALES schema was searched, so the PUBLIC tables created in setup are not generated
        val salesSource = generatedSource(generated.single())
        assertContains(salesSource, "object SalesOrders : Table(\"SALES.ORDERS\") {")
        assertContains(salesSource, "val id: Column<Int> = integer(\"ID\")")
        assertContains(salesSource, "val total: Column<BigDecimal> = decimal(\"TOTAL\", 10, 2)")
        assertContains(salesSource, "override val primaryKey: Table.PrimaryKey = PrimaryKey(id)")

        assertNoMigrationRequired(GeneratedSalesOrders)
    }

    @Test
    fun testGeneratedColumnRoundTrip() {
        transaction(database) {
            exec(
                """
                CREATE TABLE LINE_ITEMS (
                    ID INT NOT NULL PRIMARY KEY,
                    PRICE DECIMAL(10, 2) NOT NULL,
                    QTY INT NOT NULL,
                    TOTAL DECIMAL(12, 2) GENERATED ALWAYS AS (PRICE * QTY)
                )
                """.trimIndent()
            )
        }

        val generated = generate()

        val lineItemsSource = generated.sourceOf("LineItems")
        assertContains(
            lineItemsSource,
            "val total: Column<BigDecimal?> = decimal(\"TOTAL\", 12, 2).databaseGenerated().nullable()"
        )

        assertNoMigrationRequired(GeneratedLineItems)
    }

    @Test
    fun testTemporalColumnsRoundTrip() {
        transaction(database) {
            SchemaUtils.create(SourceTemporal)
        }

        val generated = generate()

        val temporalSource = generated.sourceOf("SourceTemporal")
        assertContains(temporalSource, "val day: Column<LocalDate> = date(\"DAY\")")
        assertContains(temporalSource, "val atTime: Column<LocalTime> = time(\"AT_TIME\")")
        assertContains(temporalSource, "val createdAt: Column<LocalDateTime> = datetime(\"CREATED_AT\")")
        // timestamp() declares the same TIMESTAMP(9) as datetime() and therefore comes back as datetime()
        assertContains(temporalSource, "val recordedAt: Column<LocalDateTime> = datetime(\"RECORDED_AT\")")
        assertContains(
            temporalSource,
            "val publishedAt: Column<OffsetDateTime?> = timestampWithTimeZone(\"PUBLISHED_AT\").nullable()"
        )
        // duration() is stored as BIGINT and therefore comes back as long()
        assertContains(temporalSource, "val elapsed: Column<Long> = long(\"ELAPSED\")")
        assertContains(temporalSource, "import org.jetbrains.exposed.v1.datetime.date")

        assertNoMigrationRequired(GeneratedSourceTemporal)
    }

    @Test
    fun testArrayColumnsRoundTrip() {
        transaction(database) {
            SchemaUtils.create(SourceArrays)
        }

        val generated = generate()

        val arraysSource = generated.sourceOf("SourceArrays")
        // array<Int>("IDS", 10) is regenerated in the explicit form, with the same element type and cardinality
        assertContains(arraysSource, "val ids: Column<List<Int>> = array(\"IDS\", IntegerColumnType(), 10)")
        assertContains(arraysSource, "val tags: Column<List<String>> = array(\"TAGS\", VarCharColumnType(32))")
        assertContains(arraysSource, "val prices: Column<List<BigDecimal>> = array(\"PRICES\", DecimalColumnType(10, 2))")
        assertContains(
            arraysSource,
            "val days: Column<List<LocalDate>?> = array(\"DAYS\", KotlinLocalDateColumnType()).nullable()"
        )

        assertNoMigrationRequired(GeneratedSourceArrays)
    }

    @Test
    fun testEnumerationColumnsRoundTrip() {
        transaction(database) {
            SchemaUtils.create(SourceEnumerated)
        }

        val generated = generate()

        val enumeratedSource = generated.sourceOf("SourceEnumerated")
        assertContains(enumeratedSource, "internal enum class Status { Bar, Baz, }")
        assertContains(
            enumeratedSource,
            "val status: Column<Status> = customEnumeration(\"STATUS\", \"ENUM('Bar', 'Baz')\", { Status.valueOf(it as String) }, { it.name })"
        )

        assertNoMigrationRequired(GeneratedSourceEnumerated)
    }

    @Test
    fun testJsonColumnsRoundTrip() {
        transaction(database) {
            SchemaUtils.create(SourceJson)
        }

        val generated = generate()

        val jsonSource = generated.sourceOf("SourceJson")
        assertContains(jsonSource, "val document: Column<String> = json<String>(\"DOCUMENT\", { it }, { it })")
        // jsonb() is indistinguishable from json() on H2 and is regenerated as json()
        assertContains(
            jsonSource,
            "val binaryDocument: Column<String?> = json<String>(\"BINARY_DOCUMENT\", { it }, { it }).nullable()"
        )
        assertContains(jsonSource, "import org.jetbrains.exposed.v1.json.json")
        assertTrue(
            messages.any { it.startsWith("Column 'BINARY_DOCUMENT' of table 'SOURCE_JSON':") && it.contains("jsonb()") },
            "Expected the jsonb() substitution to be logged: $messages"
        )

        assertNoMigrationRequired(GeneratedSourceJson)
    }

    @Test
    fun testMappedTemporalColumnsRoundTrip() {
        transaction(database) {
            SchemaUtils.create(SourceTemporal)
        }

        val generated = generate(
            columnMappings = listOf(
                ColumnMapping("SOURCE_TEMPORAL.RECORDED_AT", "timestamp(\"RECORDED_AT\")"),
                ColumnMapping("SOURCE_TEMPORAL.ELAPSED", "duration(\"ELAPSED\")"),
            )
        )

        val temporalSource = generated.sourceOf("SourceTemporal")
        assertContains(temporalSource, "val recordedAt = timestamp(\"RECORDED_AT\")")
        assertContains(temporalSource, "val elapsed = duration(\"ELAPSED\")")
        assertContains(temporalSource, "import org.jetbrains.exposed.v1.datetime.duration")
        assertContains(temporalSource, "import org.jetbrains.exposed.v1.datetime.timestamp")

        assertNoMigrationRequired(SourceTemporal)
    }

    @Test
    fun testForeignKeysRoundTrip() {
        transaction(database) {
            SchemaUtils.create(SourceUsers, SourceOrders)
        }

        val generated = generate()

        val ordersSource = generated.sourceOf("SourceOrders")
        assertContains(ordersSource, "val userId: Column<Int> = integer(\"USER_ID\").references(SourceUsers.id)")
        assertContains(
            ordersSource,
            "val ownerEmail: Column<String?> = varchar(\"OWNER_EMAIL\", 50).references(SourceUsers.email, onDelete = ReferenceOption.CASCADE).nullable()"
        )
        assertContains(
            ordersSource,
            "val parentId: Column<Int?> = integer(\"PARENT_ID\").references(id, onDelete = ReferenceOption.SET_NULL).nullable()"
        )
        assertFalse(ordersSource.contains("fkName"), "Exposed's default key names must be omitted: $ordersSource")
        assertFalse(ordersSource.contains("_INDEX_"), "Foreign key backing indices must not be generated: $ordersSource")

        assertNoMigrationRequired(GeneratedSourceUsers, GeneratedSourceOrders)
    }

    @Test
    fun testCompositeForeignKeysRoundTrip() {
        transaction(database) {
            SchemaUtils.create(SourceParts, SourceStock)
        }

        val generated = generate()

        val stockSource = generated.sourceOf("SourceStock")
        assertContains(
            stockSource,
            "init { foreignKey(partMaker to SourceParts.maker, partCode to SourceParts.code, onDelete = ReferenceOption.CASCADE) }"
        )
        assertFalse(stockSource.contains("name = "), "Exposed's default key name must be omitted: $stockSource")
        assertFalse(stockSource.contains("_INDEX_"), "Foreign key backing indices must not be generated: $stockSource")

        assertNoMigrationRequired(GeneratedSourceParts, GeneratedSourceStock)
    }

    @Test
    fun testIdTablesRoundTrip() {
        transaction(database) {
            SchemaUtils.create(SourceAccounts, SourceSessions, SourceSkus, SourceLines)
        }

        val generated = generate(generateIdTables = true)

        assertContains(generated.sourceOf("SourceAccounts"), "object SourceAccounts : IntIdTable(\"SOURCE_ACCOUNTS\", \"ID\") {")
        val sessionsSource = generated.sourceOf("SourceSessions")
        assertContains(sessionsSource, "object SourceSessions : UuidTable(\"SOURCE_SESSIONS\", \"ID\") {")
        assertContains(
            sessionsSource,
            "val accountId: Column<EntityID<Int>> = reference(\"ACCOUNT_ID\", SourceAccounts, onDelete = ReferenceOption.CASCADE)"
        )
        assertContains(sessionsSource, "val previousId: Column<EntityID<Uuid>?> = optReference(\"PREVIOUS_ID\", SourceSessions)")
        val skusSource = generated.sourceOf("SourceSkus")
        assertContains(skusSource, "object SourceSkus : IdTable<String>(\"SOURCE_SKUS\") {")
        assertContains(skusSource, "override val id: Column<EntityID<String>> = varchar(\"SKU\", 32).entityId()")
        val linesSource = generated.sourceOf("SourceLines")
        assertContains(linesSource, "object SourceLines : CompositeIdTable(\"SOURCE_LINES\") {")
        assertContains(linesSource, "val sessionId: Column<EntityID<Uuid>> = reference(\"SESSION_ID\", SourceSessions)")
        assertContains(linesSource, "val lineNo: Column<EntityID<Int>> = integer(\"LINE_NO\").entityId()")
        assertContains(linesSource, "val sku: Column<EntityID<String>> = reference(\"SKU\", SourceSkus)")
        assertContains(linesSource, "override val primaryKey: Table.PrimaryKey = PrimaryKey(sessionId, lineNo)")

        assertNoMigrationRequired(SourceAccounts, SourceSessions, SourceSkus, SourceLines)
    }

    @Test
    fun testSelfReferenceToLaterColumnRoundTrip() {
        transaction(database) {
            exec(
                "CREATE TABLE NODES (PARENT_ID INT NULL, ID INT NOT NULL PRIMARY KEY, " +
                    "FOREIGN KEY (PARENT_ID) REFERENCES NODES (ID) ON DELETE SET NULL ON UPDATE RESTRICT)"
            )
        }

        val generated = generate()

        val nodesSource = generated.sourceOf("Nodes")
        assertContains(nodesSource, "val parentId: Column<Int?> = integer(\"PARENT_ID\").nullable()")
        assertContains(nodesSource, "init { foreignKey(parentId to id, onDelete = ReferenceOption.SET_NULL) }")

        assertNoMigrationRequired(GeneratedNodes)
    }

    @Test
    fun testDefaultsRoundTrip() {
        transaction(database) {
            SchemaUtils.create(SourceDefaults)
        }

        val generated = generate()

        val defaultsSource = generated.sourceOf("SourceDefaults")
        assertContains(defaultsSource, "val retries: Column<Int> = integer(\"RETRIES\").default(3)")
        assertContains(defaultsSource, "val offsetMs: Column<Long> = long(\"OFFSET_MS\").default(-500L)")
        assertContains(defaultsSource, "val ratio: Column<Double> = double(\"RATIO\").default(0.75)")
        assertContains(
            defaultsSource,
            "val price: Column<BigDecimal> = decimal(\"PRICE\", 10, 2).default(BigDecimal(\"12.50\"))"
        )
        assertContains(defaultsSource, "val active: Column<Boolean> = bool(\"ACTIVE\").default(true)")
        assertContains(defaultsSource, "val label: Column<String> = varchar(\"LABEL\", 20).default(\"pending\")")
        assertContains(defaultsSource, "val note: Column<String?> = varchar(\"NOTE\", 10).default(\"\").nullable()")
        assertContains(
            defaultsSource,
            "val createdAt: Column<LocalDateTime> = datetime(\"CREATED_AT\").defaultExpression(CurrentDateTime)"
        )
        assertContains(defaultsSource, "val startsOn: Column<LocalDate> = date(\"STARTS_ON\").defaultExpression(CurrentDate)")

        assertNoMigrationRequired(SourceDefaults)
    }

    @Test
    fun testExposedCreatedSchemaRoundTripsWithJavaUuidMapping() {
        val generated = generate(uuidColumnType = UuidColumnType.JAVA)

        val typesSource = generated.sourceOf("SourceTypes")
        assertContains(typesSource, "val uniqueId: Column<UUID> = javaUUID(\"UNIQUE_ID\")")
        assertContains(typesSource, "val javaId: Column<UUID> = javaUUID(\"JAVA_ID\")")

        assertNoMigrationRequired(GeneratedSourceTypesJavaUuid, GeneratedSourceIndexed)
    }

    /**
     * Asserts that the mirror [tables], used by Exposed against the schema created in [setup], resolve the existing
     * tables. Also checks that Exposed's migration methods don't emit any statements when invoked.
     */
    private fun assertNoMigrationRequired(vararg tables: Table) {
        transaction(database) {
            tables.forEach { table ->
                assertTrue(table.exists(), "Generated definition of ${table.tableName} should resolve the existing table")
            }
            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(*tables, withLogs = false)
            assertTrue(statements.isEmpty(), "Expected no migration statements for generated definitions, but got: $statements")
        }
    }
}
