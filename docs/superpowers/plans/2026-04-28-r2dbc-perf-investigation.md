# R2DBC Performance Investigation — Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build measurement infrastructure (JMH micro-benchmarks + end-to-end app) for the `exposed-perf-tests` module so we can investigate EXPOSED-1003 and identify worthwhile optimizations.

**Architecture:** Two layers in `exposed-perf-tests`: (1) JMH benchmarks under `src/jmh/kotlin` for per-call type-mapper costs, (2) a Gradle `application`-plugin runnable app under `src/main/kotlin` running SELECT/INSERT/UPDATE scenarios against four backends (raw JDBC, Exposed JDBC, raw R2DBC, Exposed R2DBC) on H2 in-memory. Output is markdown tables captured in `benchmark-results.md`.

**Tech Stack:** Kotlin 2.2, Gradle, JMH (`me.champeau.jmh` plugin), H2 (JDBC + R2DBC), Exposed JDBC + R2DBC, kotlinx-coroutines.

**Reference spec:** `docs/superpowers/specs/2026-04-28-r2dbc-perf-investigation-design.md`

---

## File Structure

```
exposed-perf-tests/
├── build.gradle.kts
├── PROFILING.md
├── benchmark-results.md
├── src/
│   ├── main/kotlin/org/jetbrains/exposed/v1/perf/
│   │   ├── Main.kt                              ← entry point
│   │   ├── Bench.kt                             ← warmup + measure + median helpers
│   │   ├── Tables.kt                            ← Exposed table definition + raw SQL DDL
│   │   ├── DatabaseSetup.kt                     ← JDBC + R2DBC connection helpers, schema setup, seed
│   │   ├── Scenario.kt                          ← Scenario interface
│   │   ├── scenarios/
│   │   │   ├── SelectByPk.kt                    ← 4 backend impls
│   │   │   ├── SelectMany.kt                    ← 4 backend impls (parameterized 100 / 1000)
│   │   │   ├── InsertSingle.kt
│   │   │   ├── InsertBatch.kt
│   │   │   └── UpdateSingle.kt
│   │   └── report/
│   │       └── MarkdownReport.kt
│   └── jmh/kotlin/org/jetbrains/exposed/v1/perf/jmh/
│       ├── TypeMapperResolutionBenchmark.kt
│       ├── TypeMapperAllocationBenchmark.kt
│       └── MapperPropertyAccessBenchmark.kt
```

`settings.gradle.kts` is modified to register the new module.

---

### Task 1: Register module and create build.gradle.kts

**Files:**
- Modify: `settings.gradle.kts` — add `include("exposed-perf-tests")`
- Create: `exposed-perf-tests/build.gradle.kts`
- Create: `exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/Main.kt` (placeholder)

- [ ] **Step 1: Add module to settings**

In `settings.gradle.kts`, after the line `include("exposed-jdbc-r2dbc-tests")`, add:

```kotlin
include("exposed-perf-tests")
```

- [ ] **Step 2: Create build.gradle.kts**

Create `exposed-perf-tests/build.gradle.kts` with this exact content:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    application
    id("me.champeau.jmh") version "0.7.2"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("org.jetbrains.exposed.v1.perf.MainKt")
}

dependencies {
    implementation(project(":exposed-core"))
    implementation(project(":exposed-jdbc"))
    implementation(project(":exposed-r2dbc"))

    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.coroutines.reactive)
    implementation(libs.r2dbc.spi)

    runtimeOnly(libs.h2)
    runtimeOnly(libs.r2dbc.h2) {
        exclude(group = "com.h2database", module = "h2")
    }
    implementation(libs.h2)
    implementation(libs.r2dbc.h2) {
        exclude(group = "com.h2database", module = "h2")
    }

    implementation(libs.slf4j)
    implementation(libs.log4j.slf4j.impl)
    implementation(libs.log4j.api)
    implementation(libs.log4j.core)

    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

jmh {
    warmupIterations.set(5)
    iterations.set(10)
    fork.set(1)
    timeUnit.set("ns")
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    targetCompatibility = "17"
}
```

- [ ] **Step 3: Create placeholder Main.kt**

Create `exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/Main.kt`:

```kotlin
package org.jetbrains.exposed.v1.perf

fun main() {
    println("exposed-perf-tests: placeholder")
}
```

- [ ] **Step 4: Verify module compiles and runs**

Run: `./gradlew :exposed-perf-tests:run`

Expected last line of output before BUILD SUCCESSFUL: `exposed-perf-tests: placeholder`

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts exposed-perf-tests/build.gradle.kts exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/Main.kt
git commit -m "build: scaffold exposed-perf-tests module"
```

---

### Task 2: JMH smoke benchmark

**Files:**
- Create: `exposed-perf-tests/src/jmh/kotlin/org/jetbrains/exposed/v1/perf/jmh/SmokeBenchmark.kt`

- [ ] **Step 1: Create smoke benchmark**

Create `exposed-perf-tests/src/jmh/kotlin/org/jetbrains/exposed/v1/perf/jmh/SmokeBenchmark.kt`:

```kotlin
package org.jetbrains.exposed.v1.perf.jmh

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class SmokeBenchmark {
    @Benchmark
    fun add(): Int = 1 + 1
}
```

- [ ] **Step 2: Verify JMH builds and runs**

Run: `./gradlew :exposed-perf-tests:jmh -PjmhInclude=SmokeBenchmark`

Expected: BUILD SUCCESSFUL, with a results table containing a row for `SmokeBenchmark.add` showing a small `ns/op` (~1-3 ns).

- [ ] **Step 3: Commit**

```bash
git add exposed-perf-tests/src/jmh/kotlin/org/jetbrains/exposed/v1/perf/jmh/SmokeBenchmark.kt
git commit -m "build: add JMH smoke benchmark"
```

---

### Task 3: Tables and database setup

**Files:**
- Create: `exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/Tables.kt`
- Create: `exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/DatabaseSetup.kt`

- [ ] **Step 1: Create Tables.kt**

Create `exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/Tables.kt`:

```kotlin
package org.jetbrains.exposed.v1.perf

import org.jetbrains.exposed.v1.core.Table

object Customers : Table("customers") {
    val id = integer("id")
    val name = varchar("name", 64)
    val age = integer("age")
    val email = varchar("email", 128)
    override val primaryKey = PrimaryKey(id)
}

const val CREATE_TABLE_SQL = """
    CREATE TABLE IF NOT EXISTS customers (
        id INT PRIMARY KEY,
        name VARCHAR(64) NOT NULL,
        age INT NOT NULL,
        email VARCHAR(128) NOT NULL
    )
"""

const val DROP_TABLE_SQL = "DROP TABLE IF EXISTS customers"
const val INSERT_SQL = "INSERT INTO customers (id, name, age, email) VALUES (?, ?, ?, ?)"
const val SELECT_BY_PK_SQL = "SELECT id, name, age, email FROM customers WHERE id = ?"
const val SELECT_LIMIT_SQL = "SELECT id, name, age, email FROM customers WHERE id <= ?"
const val UPDATE_SQL = "UPDATE customers SET age = ? WHERE id = ?"
const val DELETE_ALL_SQL = "DELETE FROM customers"
```

- [ ] **Step 2: Create DatabaseSetup.kt**

Create `exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/DatabaseSetup.kt`:

```kotlin
package org.jetbrains.exposed.v1.perf

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import java.sql.Connection
import java.sql.DriverManager

const val JDBC_URL = "jdbc:h2:mem:perfdb;DB_CLOSE_DELAY=-1"
const val R2DBC_URL = "r2dbc:h2:mem:///perfdb"
const val SEED_ROW_COUNT = 1000

object Backends {
    val jdbcConnection: Connection by lazy {
        DriverManager.getConnection(JDBC_URL)
    }
    val exposedJdbcDb: Database by lazy {
        Database.connect(JDBC_URL)
    }
    val r2dbcFactory: ConnectionFactory by lazy {
        ConnectionFactories.get(R2DBC_URL)
    }
    val exposedR2dbcDb: R2dbcDatabase by lazy {
        R2dbcDatabase.connect(R2DBC_URL)
    }
}

fun setUpSchemaAndSeedData() {
    val conn = Backends.jdbcConnection
    conn.createStatement().use { it.execute(DROP_TABLE_SQL) }
    conn.createStatement().use { it.execute(CREATE_TABLE_SQL.trimIndent()) }

    conn.prepareStatement(INSERT_SQL).use { ps ->
        for (i in 1..SEED_ROW_COUNT) {
            ps.setInt(1, i)
            ps.setString(2, "name_$i")
            ps.setInt(3, 20 + (i % 50))
            ps.setString(4, "user$i@example.com")
            ps.addBatch()
        }
        ps.executeBatch()
    }
}

suspend fun verifyR2dbcSeesSeedData(): Int {
    val conn = Backends.r2dbcFactory.create().awaitFirstOrNull()!!
    try {
        val result = conn.createStatement("SELECT COUNT(*) AS c FROM customers").execute()
        var count = 0
        kotlinx.coroutines.reactive.collect(result) { r ->
            r.map { row, _ -> count = (row.get(0) as Number).toInt() }.collect {}
        }
        return count
    } finally {
        conn.close().awaitFirstOrNull()
    }
}
```

- [ ] **Step 3: Update Main.kt to verify setup works**

Replace `exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/Main.kt` with:

```kotlin
package org.jetbrains.exposed.v1.perf

import kotlinx.coroutines.runBlocking

fun main() {
    setUpSchemaAndSeedData()

    Backends.jdbcConnection.prepareStatement("SELECT COUNT(*) FROM customers").use { ps ->
        ps.executeQuery().use { rs ->
            check(rs.next())
            val count = rs.getInt(1)
            println("JDBC sees $count rows")
            check(count == SEED_ROW_COUNT) { "Expected $SEED_ROW_COUNT rows via JDBC, got $count" }
        }
    }

    runBlocking {
        val r2dbcCount = verifyR2dbcSeesSeedData()
        println("R2DBC sees $r2dbcCount rows")
        check(r2dbcCount == SEED_ROW_COUNT) { "Expected $SEED_ROW_COUNT rows via R2DBC, got $r2dbcCount" }
    }

    println("Setup OK")
}
```

- [ ] **Step 4: Run and verify**

Run: `./gradlew :exposed-perf-tests:run`

Expected output (last lines):
```
JDBC sees 1000 rows
R2DBC sees 1000 rows
Setup OK
```

- [ ] **Step 5: Commit**

```bash
git add exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/Tables.kt \
        exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/DatabaseSetup.kt \
        exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/Main.kt
git commit -m "feat: add Tables and DatabaseSetup for perf tests"
```

---

### Task 4: Scenario interface + benchmark loop

**Files:**
- Create: `exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/Scenario.kt`
- Create: `exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/Bench.kt`

- [ ] **Step 1: Create Scenario.kt**

Create `exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/Scenario.kt`:

```kotlin
package org.jetbrains.exposed.v1.perf

/**
 * One scenario produces 4 named "backend" runners. Each runner does one unit of work
 * (e.g. one SELECT) and asserts correctness internally.
 */
data class Scenario(
    val name: String,
    val rawJdbc: () -> Unit,
    val exposedJdbc: () -> Unit,
    val rawR2dbc: suspend () -> Unit,
    val exposedR2dbc: suspend () -> Unit,
)
```

- [ ] **Step 2: Create Bench.kt**

Create `exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/Bench.kt`:

```kotlin
package org.jetbrains.exposed.v1.perf

import kotlinx.coroutines.runBlocking

const val WARMUP_OPS = 1_000
const val MEASURE_ITERATIONS = 10
const val MEASURE_OPS = 10_000

data class BackendResult(val backend: String, val medianNsPerOp: Long)

data class ScenarioResult(val scenario: String, val results: List<BackendResult>)

fun benchSync(name: String, op: () -> Unit): Long {
    repeat(WARMUP_OPS) { op() }

    val timings = LongArray(MEASURE_ITERATIONS)
    for (i in 0 until MEASURE_ITERATIONS) {
        val start = System.nanoTime()
        repeat(MEASURE_OPS) { op() }
        timings[i] = (System.nanoTime() - start) / MEASURE_OPS
    }
    timings.sort()
    return timings[MEASURE_ITERATIONS / 2]
}

fun benchSuspend(name: String, op: suspend () -> Unit): Long = runBlocking {
    repeat(WARMUP_OPS) { op() }

    val timings = LongArray(MEASURE_ITERATIONS)
    for (i in 0 until MEASURE_ITERATIONS) {
        val start = System.nanoTime()
        repeat(MEASURE_OPS) { op() }
        timings[i] = (System.nanoTime() - start) / MEASURE_OPS
    }
    timings.sort()
    timings[MEASURE_ITERATIONS / 2]
}

fun runScenario(scenario: Scenario): ScenarioResult {
    return ScenarioResult(
        scenario = scenario.name,
        results = listOf(
            BackendResult("Raw JDBC", benchSync(scenario.name + "/raw-jdbc", scenario.rawJdbc)),
            BackendResult("Exposed JDBC", benchSync(scenario.name + "/exposed-jdbc", scenario.exposedJdbc)),
            BackendResult("Raw R2DBC", benchSuspend(scenario.name + "/raw-r2dbc", scenario.rawR2dbc)),
            BackendResult("Exposed R2DBC", benchSuspend(scenario.name + "/exposed-r2dbc", scenario.exposedR2dbc)),
        )
    )
}
```

- [ ] **Step 3: Compile**

Run: `./gradlew :exposed-perf-tests:compileKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/Scenario.kt \
        exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/Bench.kt
git commit -m "feat: add Scenario interface and Bench harness"
```

---

### Task 5: SelectByPk scenario

**Files:**
- Create: `exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/scenarios/SelectByPk.kt`

- [ ] **Step 1: Create scenario file**

Create `exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/scenarios/SelectByPk.kt`:

```kotlin
package org.jetbrains.exposed.v1.perf.scenarios

import io.r2dbc.spi.Connection
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.collect
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.perf.Backends
import org.jetbrains.exposed.v1.perf.Customers
import org.jetbrains.exposed.v1.perf.SELECT_BY_PK_SQL
import org.jetbrains.exposed.v1.perf.Scenario
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import kotlinx.coroutines.flow.first as flowFirst
import kotlinx.coroutines.flow.toList

fun selectByPkScenario(): Scenario {
    val pk = 500
    val expectedName = "name_$pk"

    return Scenario(
        name = "SELECT 1 row by PK",
        rawJdbc = {
            Backends.jdbcConnection.prepareStatement(SELECT_BY_PK_SQL).use { ps ->
                ps.setInt(1, pk)
                ps.executeQuery().use { rs ->
                    check(rs.next())
                    val name = rs.getString(2)
                    check(name == expectedName)
                }
            }
        },
        exposedJdbc = {
            transaction(Backends.exposedJdbcDb) {
                val rows = Customers.selectAll().where { Customers.id eq pk }.toList()
                check(rows.size == 1)
                check(rows[0][Customers.name] == expectedName)
            }
        },
        rawR2dbc = {
            val conn: Connection = Backends.r2dbcFactory.create().awaitFirstOrNull()!!
            try {
                val result = conn.createStatement(SELECT_BY_PK_SQL.replace("?", "$1"))
                    .bind("$1", pk)
                    .execute()
                var found = 0
                var name = ""
                result.collect { r ->
                    r.map { row, _ -> name = row.get(1, java.lang.String::class.java).toString() }.collect { found++ }
                }
                check(found == 1)
                check(name == expectedName)
            } finally {
                conn.close().awaitFirstOrNull()
            }
        },
        exposedR2dbc = {
            suspendTransaction(Backends.exposedR2dbcDb) {
                val rows = Customers.selectAll().where { Customers.id eq pk }.toList()
                check(rows.size == 1)
                check(rows[0][Customers.name] == expectedName)
            }
        },
    )
}
```

- [ ] **Step 2: Update Main.kt to run this scenario**

Replace `exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/Main.kt` with:

```kotlin
package org.jetbrains.exposed.v1.perf

import org.jetbrains.exposed.v1.perf.scenarios.selectByPkScenario

fun main() {
    setUpSchemaAndSeedData()

    val scenarios = listOf(
        selectByPkScenario(),
    )

    for (scenario in scenarios) {
        val result = runScenario(scenario)
        println("=== ${result.scenario} ===")
        for (r in result.results) {
            println("  ${r.backend}: ${r.medianNsPerOp} ns/op")
        }
    }
}
```

- [ ] **Step 3: Run and verify**

Run: `./gradlew :exposed-perf-tests:run`

Expected: BUILD SUCCESSFUL, with output containing:
```
=== SELECT 1 row by PK ===
  Raw JDBC: <number> ns/op
  Exposed JDBC: <number> ns/op
  Raw R2DBC: <number> ns/op
  Exposed R2DBC: <number> ns/op
```

If any backend's correctness `check()` fails the run fails — this is the cross-backend correctness assertion.

- [ ] **Step 4: Commit**

```bash
git add exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/scenarios/SelectByPk.kt \
        exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/Main.kt
git commit -m "feat: add SelectByPk scenario across 4 backends"
```

---

### Task 6: SelectMany scenario (100 + 1000 rows)

**Files:**
- Create: `exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/scenarios/SelectMany.kt`

- [ ] **Step 1: Create scenario file**

Create `exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/scenarios/SelectMany.kt`:

```kotlin
package org.jetbrains.exposed.v1.perf.scenarios

import io.r2dbc.spi.Connection
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.collect
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.perf.Backends
import org.jetbrains.exposed.v1.perf.Customers
import org.jetbrains.exposed.v1.perf.SELECT_LIMIT_SQL
import org.jetbrains.exposed.v1.perf.Scenario
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

fun selectManyScenario(rowCount: Int): Scenario {
    return Scenario(
        name = "SELECT ~$rowCount rows",
        rawJdbc = {
            Backends.jdbcConnection.prepareStatement(SELECT_LIMIT_SQL).use { ps ->
                ps.setInt(1, rowCount)
                ps.executeQuery().use { rs ->
                    var count = 0
                    while (rs.next()) {
                        rs.getInt(1)
                        rs.getString(2)
                        rs.getInt(3)
                        rs.getString(4)
                        count++
                    }
                    check(count == rowCount)
                }
            }
        },
        exposedJdbc = {
            transaction(Backends.exposedJdbcDb) {
                val rows = Customers.selectAll().where { Customers.id lessEq rowCount }.toList()
                check(rows.size == rowCount)
            }
        },
        rawR2dbc = {
            val conn: Connection = Backends.r2dbcFactory.create().awaitFirstOrNull()!!
            try {
                val result = conn.createStatement(SELECT_LIMIT_SQL.replace("?", "$1"))
                    .bind("$1", rowCount)
                    .execute()
                var count = 0
                result.collect { r ->
                    r.map { row, _ ->
                        row.get(0, java.lang.Integer::class.java)
                        row.get(1, java.lang.String::class.java)
                        row.get(2, java.lang.Integer::class.java)
                        row.get(3, java.lang.String::class.java)
                    }.collect { count++ }
                }
                check(count == rowCount)
            } finally {
                conn.close().awaitFirstOrNull()
            }
        },
        exposedR2dbc = {
            suspendTransaction(Backends.exposedR2dbcDb) {
                val rows = Customers.selectAll().where { Customers.id lessEq rowCount }.toList()
                check(rows.size == rowCount)
            }
        },
    )
}
```

- [ ] **Step 2: Update Main.kt**

Replace the scenarios list in `Main.kt` with:

```kotlin
import org.jetbrains.exposed.v1.perf.scenarios.selectManyScenario

// inside main():
val scenarios = listOf(
    selectByPkScenario(),
    selectManyScenario(rowCount = 100),
    selectManyScenario(rowCount = 1000),
)
```

- [ ] **Step 3: Run and verify**

Run: `./gradlew :exposed-perf-tests:run`

Expected: 3 scenario blocks printed, each with 4 backend timings, no `IllegalStateException` from check assertions.

- [ ] **Step 4: Commit**

```bash
git add exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/scenarios/SelectMany.kt \
        exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/Main.kt
git commit -m "feat: add SelectMany scenario (100 / 1000 rows)"
```

---

### Task 7: InsertSingle scenario

**Files:**
- Create: `exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/scenarios/InsertSingle.kt`

- [ ] **Step 1: Create scenario file**

Create `exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/scenarios/InsertSingle.kt`:

```kotlin
package org.jetbrains.exposed.v1.perf.scenarios

import io.r2dbc.spi.Connection
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.collect
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.perf.Backends
import org.jetbrains.exposed.v1.perf.Customers
import org.jetbrains.exposed.v1.perf.INSERT_SQL
import org.jetbrains.exposed.v1.perf.Scenario
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import java.util.concurrent.atomic.AtomicInteger

fun insertSingleScenario(): Scenario {
    val nextId = AtomicInteger(1_000_000)

    return Scenario(
        name = "INSERT 1 row",
        rawJdbc = {
            val id = nextId.getAndIncrement()
            Backends.jdbcConnection.prepareStatement(INSERT_SQL).use { ps ->
                ps.setInt(1, id)
                ps.setString(2, "name_$id")
                ps.setInt(3, 30)
                ps.setString(4, "u$id@e.com")
                check(ps.executeUpdate() == 1)
            }
        },
        exposedJdbc = {
            val id = nextId.getAndIncrement()
            transaction(Backends.exposedJdbcDb) {
                Customers.insert {
                    it[Customers.id] = id
                    it[name] = "name_$id"
                    it[age] = 30
                    it[email] = "u$id@e.com"
                }
            }
        },
        rawR2dbc = {
            val id = nextId.getAndIncrement()
            val conn: Connection = Backends.r2dbcFactory.create().awaitFirstOrNull()!!
            try {
                val result = conn.createStatement("INSERT INTO customers (id, name, age, email) VALUES ($1, $2, $3, $4)")
                    .bind("$1", id)
                    .bind("$2", "name_$id")
                    .bind("$3", 30)
                    .bind("$4", "u$id@e.com")
                    .execute()
                var updated = 0L
                result.collect { r ->
                    r.rowsUpdated.collect { updated += it }
                }
                check(updated == 1L)
            } finally {
                conn.close().awaitFirstOrNull()
            }
        },
        exposedR2dbc = {
            val id = nextId.getAndIncrement()
            suspendTransaction(Backends.exposedR2dbcDb) {
                Customers.insert {
                    it[Customers.id] = id
                    it[name] = "name_$id"
                    it[age] = 30
                    it[email] = "u$id@e.com"
                }
            }
        },
    )
}
```

- [ ] **Step 2: Update Main.kt**

Add `insertSingleScenario()` to the scenarios list in `Main.kt`:

```kotlin
import org.jetbrains.exposed.v1.perf.scenarios.insertSingleScenario

// inside main():
val scenarios = listOf(
    selectByPkScenario(),
    selectManyScenario(rowCount = 100),
    selectManyScenario(rowCount = 1000),
    insertSingleScenario(),
)
```

- [ ] **Step 3: Run and verify**

Run: `./gradlew :exposed-perf-tests:run`

Expected: includes `=== INSERT 1 row ===` block with 4 backend timings.

- [ ] **Step 4: Commit**

```bash
git add exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/scenarios/InsertSingle.kt \
        exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/Main.kt
git commit -m "feat: add InsertSingle scenario"
```

---

### Task 8: InsertBatch scenario (10 rows)

**Files:**
- Create: `exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/scenarios/InsertBatch.kt`

- [ ] **Step 1: Create scenario file**

Create `exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/scenarios/InsertBatch.kt`:

```kotlin
package org.jetbrains.exposed.v1.perf.scenarios

import io.r2dbc.spi.Connection
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.collect
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.perf.Backends
import org.jetbrains.exposed.v1.perf.Customers
import org.jetbrains.exposed.v1.perf.INSERT_SQL
import org.jetbrains.exposed.v1.perf.Scenario
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import java.util.concurrent.atomic.AtomicInteger

private const val BATCH_SIZE = 10

fun insertBatchScenario(): Scenario {
    val nextId = AtomicInteger(2_000_000)

    fun nextBatch(): IntRange {
        val start = nextId.getAndAdd(BATCH_SIZE)
        return start until (start + BATCH_SIZE)
    }

    return Scenario(
        name = "INSERT batch of $BATCH_SIZE rows",
        rawJdbc = {
            val ids = nextBatch()
            Backends.jdbcConnection.prepareStatement(INSERT_SQL).use { ps ->
                for (id in ids) {
                    ps.setInt(1, id)
                    ps.setString(2, "name_$id")
                    ps.setInt(3, 40)
                    ps.setString(4, "u$id@e.com")
                    ps.addBatch()
                }
                val counts = ps.executeBatch()
                check(counts.size == BATCH_SIZE)
            }
        },
        exposedJdbc = {
            val ids = nextBatch().toList()
            transaction(Backends.exposedJdbcDb) {
                Customers.batchInsert(ids) { id ->
                    this[Customers.id] = id
                    this[Customers.name] = "name_$id"
                    this[Customers.age] = 40
                    this[Customers.email] = "u$id@e.com"
                }
            }
        },
        rawR2dbc = {
            val ids = nextBatch()
            val conn: Connection = Backends.r2dbcFactory.create().awaitFirstOrNull()!!
            try {
                val stmt = conn.createStatement("INSERT INTO customers (id, name, age, email) VALUES ($1, $2, $3, $4)")
                var first = true
                for (id in ids) {
                    if (!first) stmt.add()
                    stmt.bind("$1", id)
                        .bind("$2", "name_$id")
                        .bind("$3", 40)
                        .bind("$4", "u$id@e.com")
                    first = false
                }
                var updated = 0L
                stmt.execute().collect { r ->
                    r.rowsUpdated.collect { updated += it }
                }
                check(updated == BATCH_SIZE.toLong())
            } finally {
                conn.close().awaitFirstOrNull()
            }
        },
        exposedR2dbc = {
            val ids = nextBatch().toList()
            suspendTransaction(Backends.exposedR2dbcDb) {
                Customers.batchInsert(ids) { id ->
                    this[Customers.id] = id
                    this[Customers.name] = "name_$id"
                    this[Customers.age] = 40
                    this[Customers.email] = "u$id@e.com"
                }
            }
        },
    )
}
```

- [ ] **Step 2: Update Main.kt**

Add `insertBatchScenario()` to the scenarios list in `Main.kt`:

```kotlin
import org.jetbrains.exposed.v1.perf.scenarios.insertBatchScenario

// inside main():
val scenarios = listOf(
    selectByPkScenario(),
    selectManyScenario(rowCount = 100),
    selectManyScenario(rowCount = 1000),
    insertSingleScenario(),
    insertBatchScenario(),
)
```

- [ ] **Step 3: Run and verify**

Run: `./gradlew :exposed-perf-tests:run`

Expected: includes `=== INSERT batch of 10 rows ===` with 4 timings.

- [ ] **Step 4: Commit**

```bash
git add exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/scenarios/InsertBatch.kt \
        exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/Main.kt
git commit -m "feat: add InsertBatch scenario (10 rows)"
```

---

### Task 9: UpdateSingle scenario

**Files:**
- Create: `exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/scenarios/UpdateSingle.kt`

- [ ] **Step 1: Create scenario file**

Create `exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/scenarios/UpdateSingle.kt`:

```kotlin
package org.jetbrains.exposed.v1.perf.scenarios

import io.r2dbc.spi.Connection
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.collect
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.perf.Backends
import org.jetbrains.exposed.v1.perf.Customers
import org.jetbrains.exposed.v1.perf.Scenario
import org.jetbrains.exposed.v1.perf.UPDATE_SQL
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update

fun updateSingleScenario(): Scenario {
    val pk = 750
    var ageCounter = 100

    fun nextAge(): Int = ageCounter++.also { if (it > 100_000) ageCounter = 100 }

    return Scenario(
        name = "UPDATE 1 row by PK",
        rawJdbc = {
            val newAge = nextAge()
            Backends.jdbcConnection.prepareStatement(UPDATE_SQL).use { ps ->
                ps.setInt(1, newAge)
                ps.setInt(2, pk)
                check(ps.executeUpdate() == 1)
            }
        },
        exposedJdbc = {
            val newAge = nextAge()
            transaction(Backends.exposedJdbcDb) {
                val updated = Customers.update({ Customers.id eq pk }) { it[age] = newAge }
                check(updated == 1)
            }
        },
        rawR2dbc = {
            val newAge = nextAge()
            val conn: Connection = Backends.r2dbcFactory.create().awaitFirstOrNull()!!
            try {
                val result = conn.createStatement("UPDATE customers SET age = $1 WHERE id = $2")
                    .bind("$1", newAge)
                    .bind("$2", pk)
                    .execute()
                var updated = 0L
                result.collect { r ->
                    r.rowsUpdated.collect { updated += it }
                }
                check(updated == 1L)
            } finally {
                conn.close().awaitFirstOrNull()
            }
        },
        exposedR2dbc = {
            val newAge = nextAge()
            suspendTransaction(Backends.exposedR2dbcDb) {
                val updated = Customers.update({ Customers.id eq pk }) { it[age] = newAge }
                check(updated == 1)
            }
        },
    )
}
```

- [ ] **Step 2: Update Main.kt**

Add `updateSingleScenario()` to the scenarios list:

```kotlin
import org.jetbrains.exposed.v1.perf.scenarios.updateSingleScenario

// inside main():
val scenarios = listOf(
    selectByPkScenario(),
    selectManyScenario(rowCount = 100),
    selectManyScenario(rowCount = 1000),
    insertSingleScenario(),
    insertBatchScenario(),
    updateSingleScenario(),
)
```

- [ ] **Step 3: Run and verify**

Run: `./gradlew :exposed-perf-tests:run`

Expected: 6 scenario blocks now printed, all 4 backends per block, no assertion failures.

- [ ] **Step 4: Commit**

```bash
git add exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/scenarios/UpdateSingle.kt \
        exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/Main.kt
git commit -m "feat: add UpdateSingle scenario"
```

---

### Task 10: Markdown report

**Files:**
- Create: `exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/report/MarkdownReport.kt`

- [ ] **Step 1: Create report formatter**

Create `exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/report/MarkdownReport.kt`:

```kotlin
package org.jetbrains.exposed.v1.perf.report

import org.jetbrains.exposed.v1.perf.ScenarioResult

fun formatScenarioTable(results: List<ScenarioResult>): String {
    val backends = listOf("Raw JDBC", "Exposed JDBC", "Raw R2DBC", "Exposed R2DBC")
    val sb = StringBuilder()

    sb.appendLine("### Per-scenario timings (ns/op, median over 10 iterations of 10000 ops)")
    sb.appendLine()
    sb.append("| Scenario              ")
    for (b in backends) sb.append("| ").append(b.padEnd(13)).append(" ")
    sb.appendLine("|")
    sb.append("|-----------------------")
    repeat(backends.size) { sb.append("|--------------:") }
    sb.appendLine("|")

    for (sr in results) {
        sb.append("| ").append(sr.scenario.padEnd(22, ' ')).append(" ")
        for (backend in backends) {
            val ns = sr.results.find { it.backend == backend }?.medianNsPerOp ?: -1L
            sb.append("| ").append(ns.toString().padStart(13)).append(" ")
        }
        sb.appendLine("|")
    }
    return sb.toString()
}

fun formatRatioTable(results: List<ScenarioResult>): String {
    val sb = StringBuilder()
    sb.appendLine("### Ratios")
    sb.appendLine()
    sb.appendLine("| Scenario              | Exposed-R2DBC vs Raw-R2DBC | Exposed-R2DBC vs Exposed-JDBC |")
    sb.appendLine("|-----------------------|---------------------------:|------------------------------:|")
    for (sr in results) {
        val rawR2 = sr.results.first { it.backend == "Raw R2DBC" }.medianNsPerOp.toDouble()
        val expR2 = sr.results.first { it.backend == "Exposed R2DBC" }.medianNsPerOp.toDouble()
        val expJ = sr.results.first { it.backend == "Exposed JDBC" }.medianNsPerOp.toDouble()
        val ratio1 = "%.2fx".format(expR2 / rawR2)
        val ratio2 = "%.2fx".format(expR2 / expJ)
        sb.append("| ").append(sr.scenario.padEnd(22, ' ')).append(" ")
        sb.append("| ").append(ratio1.padStart(26)).append(" ")
        sb.append("| ").append(ratio2.padStart(29)).append(" ")
        sb.appendLine("|")
    }
    return sb.toString()
}
```

- [ ] **Step 2: Update Main.kt to print markdown**

Replace `Main.kt` body so it prints the markdown report at the end:

```kotlin
package org.jetbrains.exposed.v1.perf

import org.jetbrains.exposed.v1.perf.report.formatRatioTable
import org.jetbrains.exposed.v1.perf.report.formatScenarioTable
import org.jetbrains.exposed.v1.perf.scenarios.insertBatchScenario
import org.jetbrains.exposed.v1.perf.scenarios.insertSingleScenario
import org.jetbrains.exposed.v1.perf.scenarios.selectByPkScenario
import org.jetbrains.exposed.v1.perf.scenarios.selectManyScenario
import org.jetbrains.exposed.v1.perf.scenarios.updateSingleScenario

fun main() {
    setUpSchemaAndSeedData()

    val scenarios = listOf(
        selectByPkScenario(),
        selectManyScenario(rowCount = 100),
        selectManyScenario(rowCount = 1000),
        insertSingleScenario(),
        insertBatchScenario(),
        updateSingleScenario(),
    )

    val results = scenarios.map(::runScenario)

    for (r in results) {
        println("=== ${r.scenario} ===")
        for (br in r.results) println("  ${br.backend}: ${br.medianNsPerOp} ns/op")
    }

    println()
    println("## Baseline benchmark — H2 in-memory")
    println()
    println(formatScenarioTable(results))
    println(formatRatioTable(results))
}
```

- [ ] **Step 3: Run and capture markdown output**

Run: `./gradlew :exposed-perf-tests:run`

Expected: at the end of stdout, two well-formed markdown tables (per-scenario timings + ratios). Copy the markdown for use in Task 15.

- [ ] **Step 4: Commit**

```bash
git add exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/report/MarkdownReport.kt \
        exposed-perf-tests/src/main/kotlin/org/jetbrains/exposed/v1/perf/Main.kt
git commit -m "feat: add markdown report formatter for end-to-end benchmark"
```

---

### Task 11: TypeMapperResolutionBenchmark

**Files:**
- Create: `exposed-perf-tests/src/jmh/kotlin/org/jetbrains/exposed/v1/perf/jmh/TypeMapperResolutionBenchmark.kt`

- [ ] **Step 1: Create benchmark**

Create `exposed-perf-tests/src/jmh/kotlin/org/jetbrains/exposed/v1/perf/jmh/TypeMapperResolutionBenchmark.kt`:

```kotlin
package org.jetbrains.exposed.v1.perf.jmh

import io.r2dbc.spi.Row
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.StringColumnType
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.mappers.R2dbcRegistryTypeMapping
import org.jetbrains.exposed.v1.r2dbc.mappers.R2dbcTypeMapping
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class TypeMapperResolutionBenchmark {

    @Param("Integer", "String")
    lateinit var columnTypeName: String

    private lateinit var registry: R2dbcTypeMapping
    private lateinit var dialect: H2Dialect
    private lateinit var columnType: IColumnType<*>
    private lateinit var row: Row

    @Setup
    fun setup() {
        registry = R2dbcRegistryTypeMapping.default()
        dialect = H2Dialect()
        columnType = when (columnTypeName) {
            "Integer" -> IntegerColumnType()
            "String" -> StringColumnType()
            else -> error("unknown column type $columnTypeName")
        }
        row = StubRow(when (columnTypeName) {
            "Integer" -> 42
            "String" -> "hello"
            else -> error("unknown")
        })
    }

    @Benchmark
    fun getValue(): Any? {
        return registry.getValue(row, null, 1, dialect, columnType)
    }
}

private class StubRow(private val value: Any?) : Row {
    override fun getMetadata(): io.r2dbc.spi.RowMetadata = error("not implemented")
    override fun get(index: Int): Any? = value
    override fun get(name: String): Any? = value
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> get(index: Int, type: Class<T>): T? = value as T?
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> get(name: String, type: Class<T>): T? = value as T?
}
```

- [ ] **Step 2: Run JMH benchmark**

Run: `./gradlew :exposed-perf-tests:jmh -PjmhInclude=TypeMapperResolutionBenchmark`

Expected: BUILD SUCCESSFUL, with results table containing rows like:
```
TypeMapperResolutionBenchmark.getValue        Integer  avgt   10  XXX.XXX ± Y.YYY  ns/op
TypeMapperResolutionBenchmark.getValue        String   avgt   10  XXX.XXX ± Y.YYY  ns/op
```

- [ ] **Step 3: Commit**

```bash
git add exposed-perf-tests/src/jmh/kotlin/org/jetbrains/exposed/v1/perf/jmh/TypeMapperResolutionBenchmark.kt
git commit -m "feat: add JMH benchmark for type mapper getValue"
```

---

### Task 12: TypeMapperAllocationBenchmark

**Files:**
- Create: `exposed-perf-tests/src/jmh/kotlin/org/jetbrains/exposed/v1/perf/jmh/TypeMapperAllocationBenchmark.kt`

- [ ] **Step 1: Create benchmark**

Create `exposed-perf-tests/src/jmh/kotlin/org/jetbrains/exposed/v1/perf/jmh/TypeMapperAllocationBenchmark.kt`:

```kotlin
package org.jetbrains.exposed.v1.perf.jmh

import io.r2dbc.spi.Row
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.mappers.R2dbcRegistryTypeMapping
import org.jetbrains.exposed.v1.r2dbc.mappers.R2dbcTypeMapping
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import java.util.concurrent.TimeUnit

/**
 * Run with `-prof gc` to get bytes-allocated/op:
 * ./gradlew :exposed-perf-tests:jmh -PjmhInclude=TypeMapperAllocationBenchmark -PjmhProfilers=gc
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class TypeMapperAllocationBenchmark {

    private lateinit var registry: R2dbcTypeMapping
    private lateinit var dialect: H2Dialect
    private lateinit var columnType: IColumnType<*>
    private lateinit var row: Row

    @Setup
    fun setup() {
        registry = R2dbcRegistryTypeMapping.default()
        dialect = H2Dialect()
        columnType = IntegerColumnType()
        row = AllocStubRow(42)
    }

    @Benchmark
    fun getValue(): Any? = registry.getValue(row, null, 1, dialect, columnType)
}

private class AllocStubRow(private val value: Any?) : Row {
    override fun getMetadata(): io.r2dbc.spi.RowMetadata = error("not implemented")
    override fun get(index: Int): Any? = value
    override fun get(name: String): Any? = value
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> get(index: Int, type: Class<T>): T? = value as T?
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> get(name: String, type: Class<T>): T? = value as T?
}
```

- [ ] **Step 2: Run JMH allocation benchmark**

Run: `./gradlew :exposed-perf-tests:jmh -PjmhInclude=TypeMapperAllocationBenchmark -PjmhProfilers=gc`

Expected: BUILD SUCCESSFUL, with results table including a `gc.alloc.rate.norm` column reporting B/op.

- [ ] **Step 3: Commit**

```bash
git add exposed-perf-tests/src/jmh/kotlin/org/jetbrains/exposed/v1/perf/jmh/TypeMapperAllocationBenchmark.kt
git commit -m "feat: add JMH allocation benchmark for type mapper"
```

---

### Task 13: MapperPropertyAccessBenchmark

**Files:**
- Create: `exposed-perf-tests/src/jmh/kotlin/org/jetbrains/exposed/v1/perf/jmh/MapperPropertyAccessBenchmark.kt`

- [ ] **Step 1: Create benchmark**

Create `exposed-perf-tests/src/jmh/kotlin/org/jetbrains/exposed/v1/perf/jmh/MapperPropertyAccessBenchmark.kt`:

```kotlin
package org.jetbrains.exposed.v1.perf.jmh

import org.jetbrains.exposed.v1.r2dbc.mappers.ArrayTypeMapper
import org.jetbrains.exposed.v1.r2dbc.mappers.BinaryTypeMapper
import org.jetbrains.exposed.v1.r2dbc.mappers.DateTimeTypeMapper
import org.jetbrains.exposed.v1.r2dbc.mappers.DefaultTypeMapper
import org.jetbrains.exposed.v1.r2dbc.mappers.ExposedColumnTypeMapper
import org.jetbrains.exposed.v1.r2dbc.mappers.PostgresSpecificTypeMapper
import org.jetbrains.exposed.v1.r2dbc.mappers.PrimitiveTypeMapper
import org.jetbrains.exposed.v1.r2dbc.mappers.TypeMapper
import org.jetbrains.exposed.v1.r2dbc.mappers.ValueTypeMapper
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class MapperPropertyAccessBenchmark {

    private lateinit var mappers: List<TypeMapper>

    @Setup
    fun setup() {
        mappers = listOf(
            ExposedColumnTypeMapper(),
            PrimitiveTypeMapper(),
            DateTimeTypeMapper(),
            BinaryTypeMapper(),
            ArrayTypeMapper(),
            PostgresSpecificTypeMapper(),
            ValueTypeMapper(),
            DefaultTypeMapper(),
        )
    }

    @Benchmark
    fun readDialects(bh: Blackhole) {
        for (m in mappers) bh.consume(m.dialects)
    }

    @Benchmark
    fun readColumnTypes(bh: Blackhole) {
        for (m in mappers) bh.consume(m.columnTypes)
    }
}
```

- [ ] **Step 2: Run benchmark**

Run: `./gradlew :exposed-perf-tests:jmh -PjmhInclude=MapperPropertyAccessBenchmark -PjmhProfilers=gc`

Expected: BUILD SUCCESSFUL with results for `readDialects` and `readColumnTypes`, ideally with `gc.alloc.rate.norm` showing per-op allocations.

- [ ] **Step 3: Commit**

```bash
git add exposed-perf-tests/src/jmh/kotlin/org/jetbrains/exposed/v1/perf/jmh/MapperPropertyAccessBenchmark.kt
git commit -m "feat: add JMH benchmark for mapper property access"
```

---

### Task 14: PROFILING.md

**Files:**
- Create: `exposed-perf-tests/PROFILING.md`

- [ ] **Step 1: Create profiling guide**

Create `exposed-perf-tests/PROFILING.md`:

```markdown
# Profiling the perf-tests app

This module's end-to-end app (`./gradlew :exposed-perf-tests:run`) is a long-running process suitable for profiling. The JMH benchmarks have their own JMH-native profiler integration.

## async-profiler (CPU + allocations)

1. Download async-profiler from https://github.com/async-profiler/async-profiler/releases
2. Start the app:
   ```bash
   ./gradlew :exposed-perf-tests:run
   ```
3. In another terminal, find the PID:
   ```bash
   jps | grep MainKt
   ```
4. Attach for a CPU flame graph (30 seconds):
   ```bash
   ./profiler.sh -d 30 -f cpu.html <PID>
   ```
5. For an allocation profile:
   ```bash
   ./profiler.sh -d 30 -e alloc -f alloc.html <PID>
   ```

What to look for in flame graphs:
- `R2dbcRegistryTypeMappingImpl.getMatchingMappers` — should be a hot frame for R2DBC scenarios if EXPOSED-1003 is reproducing
- `R2dbcRegistryTypeMappingImpl.getValue` — wraps the above; total time here = type-mapper overhead
- `kotlinx.coroutines.flow.*` — reactive→coroutine bridging cost
- `kotlinx.coroutines.reactive.*` — Channel signaling per emitted row

## JFR (Java Flight Recorder)

Alternative to async-profiler, built into the JDK:

1. Run the app with JFR enabled:
   ```bash
   ./gradlew :exposed-perf-tests:run \
     -Dorg.gradle.jvmargs="-XX:StartFlightRecording=duration=60s,filename=perf.jfr"
   ```
2. Open `perf.jfr` in JDK Mission Control or IntelliJ's Profiler tab.
3. Look at "Method Profiling" and "Allocation in TLAB" tabs.

## JMH built-in profilers

Run JMH benchmarks with profilers attached:

```bash
# Allocation profiling (already used in TypeMapperAllocationBenchmark)
./gradlew :exposed-perf-tests:jmh -PjmhProfilers=gc

# Stack-sampling profile
./gradlew :exposed-perf-tests:jmh -PjmhProfilers=stack

# Async-profiler (requires libasyncProfiler.so on path)
./gradlew :exposed-perf-tests:jmh -PjmhProfilers=async
```
```

- [ ] **Step 2: Verify file exists**

Run: `ls exposed-perf-tests/PROFILING.md`

Expected: file listed.

- [ ] **Step 3: Commit**

```bash
git add exposed-perf-tests/PROFILING.md
git commit -m "docs: add profiling guide for perf-tests"
```

---

### Task 15: Capture baseline + write hotspot assessment

**Files:**
- Create: `exposed-perf-tests/benchmark-results.md`

- [ ] **Step 1: Run end-to-end app and capture output**

Run: `./gradlew :exposed-perf-tests:run` and copy the markdown tables from stdout.

- [ ] **Step 2: Run JMH benchmarks and capture output**

Run: `./gradlew :exposed-perf-tests:jmh -PjmhProfilers=gc 2>&1 | tee /tmp/jmh-output.txt`

The relevant lines start with `Benchmark` and contain `avgt` mode results — copy those.

- [ ] **Step 3: Profile a representative scenario**

Following PROFILING.md, attach async-profiler (or JFR) to the running app and capture a CPU + allocation flame graph during the SELECT-1000 scenario. Note the top 3 frames by self-time and top 3 allocation sites.

- [ ] **Step 4: Write benchmark-results.md**

Create `exposed-perf-tests/benchmark-results.md` with this structure (replace placeholders with actual captured data — DO NOT commit fake numbers):

```markdown
# Baseline benchmark results — `obabichev/exposed-1003-r2dbc-performance` @ <commit-sha>

Captured on: <date>
Hardware: <CPU>, <RAM>, <OS>
JDK: <java -version output>

## End-to-end (H2 in-memory)

<paste the per-scenario timings table here>

<paste the ratios table here>

## JMH micro-benchmarks

### TypeMapperResolutionBenchmark

<paste the table — Benchmark, Param, Mode, Cnt, Score, Error, Units>

### TypeMapperAllocationBenchmark (with -prof gc)

<paste — include alloc.rate.norm column>

### MapperPropertyAccessBenchmark (with -prof gc)

<paste>

## Profile findings

### CPU (top 3 frames during SELECT-1000)

1. <frame> — <self-time-%>
2. <frame> — <self-time-%>
3. <frame> — <self-time-%>

### Allocation (top 3 sites during SELECT-1000)

1. <type> — <bytes/op>
2. <type> — <bytes/op>
3. <type> — <bytes/op>

## Phase 2 hotspot assessment

Based on the data above, the top hotspots that Phase 2 should target are:

1. **<hotspot name>** — <one-paragraph rationale: data point, expected fix, estimated impact>
2. **<hotspot name>** — <rationale>
3. **<hotspot name>** — <rationale>

Hotspots considered and rejected (with reason):

- **<hotspot>** — <reason it's not worth fixing: e.g., < 1% of total, or cap is set by R2DBC driver itself>

## Conclusion

<2-3 sentences: total Exposed-R2DBC overhead vs raw R2DBC, whether EXPOSED-1003's specific claim is confirmed, and what Phase 2 will deliver>
```

- [ ] **Step 5: Verify the doc**

Open `exposed-perf-tests/benchmark-results.md`. Every placeholder (`<...>`) must be replaced with real captured data. The "Phase 2 hotspot assessment" section must name 3 specific hotspots backed by data points from the tables above.

- [ ] **Step 6: Commit**

```bash
git add exposed-perf-tests/benchmark-results.md
git commit -m "docs: capture R2DBC baseline benchmark results and hotspot assessment"
```

---

## Self-review against spec

- ✅ **Module setup** (spec file 1: `build.gradle.kts`) — Task 1
- ✅ **JMH micro-benchmarks** (spec files 2-4) — Tasks 11, 12, 13 (with smoke benchmark in Task 2 to validate plugin)
- ✅ **End-to-end app entry point** (spec file 5: `Main.kt`) — Task 1 + iteratively expanded in Tasks 3, 5-10
- ✅ **6 scenario files** (spec files 6-10) — Tasks 5, 6, 7, 8, 9 (`SelectMany.kt` covers both 100 and 1000 row variants per spec scenario list)
- ✅ **MarkdownReport** (spec file 11) — Task 10
- ✅ **PROFILING.md** (spec file 12) — Task 14
- ✅ **benchmark-results.md** (spec file 13) — Task 15
- ✅ **Definition of done** — Task 15 produces all required outputs and the hotspot assessment

**Spec deviations** (intentional, documented):
- Spec lists `resolveMatchingMappers()` as a JMH benchmark variant requiring access to private `getMatchingMappers`. The plan drops that variant and only benchmarks public `getValue`/`setValue`. This stays within scope (no changes to `exposed-r2dbc`) while still measuring the path — `getMatchingMappers` cost is included in `getValue` time, and JMH `-prof gc` still attributes its allocations.
