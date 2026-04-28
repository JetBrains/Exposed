# R2DBC Performance Investigation — Phase 1 Design

## Goal

Build the measurement infrastructure needed to investigate EXPOSED-1003 (R2DBC ~30-80% slower than JDBC, with `R2dbcRegistryTypeMappingImpl` named as a hotspot). Establish a baseline so we can identify which optimizations are worth pursuing in Phase 2.

**Out of scope for Phase 1**: any code changes to `exposed-r2dbc`, `exposed-core`, or other production modules. Phase 1 produces measurements only; Phase 2 (a separate design doc) implements the fixes.

## Context

EXPOSED-1003 reports that `R2dbcRegistryTypeMappingImpl.getMatchingMappers` rescans all registered mappers on every column read/write and that `mapper.dialects` / `mapper.columnTypes` getters allocate fresh collections per call. The reporter provided a workaround caching the resolved list per `(dialect, columnType)` pair plus pre-computed matchers.

Before applying any fix, we need to:
1. Confirm the hotspot under realistic workloads
2. Identify other hotspots that may dominate after `getMatchingMappers` is fixed
3. Quantify the inherent R2DBC vs JDBC gap (driver-level) so we know the ceiling on what Exposed-level fixes can achieve
4. Capture baseline numbers we can diff against in Phase 2

## Architecture

The `exposed-perf-tests` module gets two independent measurement layers:

```
exposed-perf-tests/
├── src/jmh/kotlin/         ← JMH micro-benchmarks (per-call cost)
├── src/main/kotlin/        ← End-to-end runnable app (full query cost)
├── PROFILING.md            ← async-profiler / JFR usage notes
└── benchmark-results.md    ← captured baseline numbers (committed)
```

The two layers answer different questions:
- **Micro (JMH)**: how much CPU/allocation does `getMatchingMappers` cost per call?
- **End-to-end (app)**: how much faster/slower is Exposed-on-R2DBC vs raw R2DBC for a 1000-row SELECT?

The end-to-end app also serves as the input for profiling (attach async-profiler to its process).

## JMH micro-benchmarks

Three benchmark classes, each focused on one hotspot:

### `TypeMapperResolutionBenchmark`
Measures `getMatchingMappers` cost.

- State: pre-built `R2dbcRegistryTypeMappingImpl` with default mappers, an `H2Dialect` instance, and column type instances (`IntegerColumnType`, `StringColumnType`, plus a JSON-style column type to exercise dialect-specific mappers)
- Benchmarks (each parameterized over column type):
  - `resolveMatchingMappers()` — calls `getMatchingMappers(dialect, columnType)` directly. Visibility relaxation needed since the method is `private`; we either reflect or add a test-only public surface.
  - `getValueViaMockRow()` — calls full `getValue(...)` with a mock `Row` returning a constant
  - `setValueViaMockStatement()` — calls full `setValue(...)` with a mock `Statement`

### `TypeMapperAllocationBenchmark`
Measures allocation rate.

- Same setup as above
- Uses `@BenchmarkMode(Mode.SingleShotTime)` with `-prof gc` to get bytes-allocated/op
- Confirms or refutes the issue's specific claims about `KClass[]`, `Arrays$ArrayList`, `Collections$SingletonList` allocations

### `MapperPropertyAccessBenchmark`
Measures `mapper.dialects` / `mapper.columnTypes` getter cost in isolation.

- Cycles through default mappers, calls each getter
- Validates whether per-getter allocation is the dominant cost or a small fraction

Each benchmark gets `@Warmup(iterations = 5)` and `@Measurement(iterations = 10)`. Output: `ns/op` and `B/op`.

## End-to-end app

A single `main()` function that runs scenarios against four backends and prints a markdown table.

### Scenarios
1. SELECT 1 row by PK
2. SELECT ~100 rows
3. SELECT ~1000 rows
4. INSERT single row + return generated key
5. INSERT batch of 10
6. UPDATE single row by PK

### Backends
1. **Raw JDBC driver** (no Exposed) — `Connection.prepareStatement`, manual `ResultSet`
2. **Exposed JDBC** — `transaction { Table.selectAll().where { ... } }`
3. **Raw R2DBC driver** (no Exposed) — `ConnectionFactory.create()`, `Statement.bind()`, manual `Result.map(...)`
4. **Exposed R2DBC** — `suspendTransaction { Table.selectAll().where { ... }.toList() }`

### Database
H2 in-memory only (no Docker, no network noise). Same JDBC URL pattern (`jdbc:h2:mem:perfdb;DB_CLOSE_DELAY=-1`) and R2DBC URL (`r2dbc:h2:mem:///perfdb`) so both connect to the same engine kind.

**Why H2 only**: the issue is about Exposed overhead, not driver speed. H2 in-memory removes I/O variance and isolates Exposed's contribution. We can add Postgres later if Phase 2 finds it useful.

### Methodology
- **Pre-warm**: run each scenario 1000 times before measuring (JIT, connection-pool warmup)
- **Measure**: 10 iterations of N=10000 ops; report median ns/op
- **Output**: per-scenario `<backend>: <median_ns> ns/op` log lines; final markdown table on stdout

### Run command
`./gradlew :exposed-perf-tests:run`

## Output format

Both layers write into a single committed report file at `exposed-perf-tests/benchmark-results.md`.

### End-to-end timings table

```markdown
## Baseline benchmark — H2 in-memory

### Per-scenario timings (ns/op, median over 10 runs of 10000 ops)

| Scenario              | Raw JDBC | Exposed JDBC | Raw R2DBC | Exposed R2DBC |
|-----------------------|---------:|-------------:|----------:|--------------:|
| SELECT 1 row by PK    |    3500  |        4200  |     9000  |        21500  |
| SELECT ~100 rows      |   48800  |       62000  |    98600  |       350000  |
| ...                   |          |              |           |               |
```

### Ratios table

```markdown
| Scenario              | Exposed-R2DBC vs Raw-R2DBC | Exposed-R2DBC vs Exposed-JDBC |
|-----------------------|---------------------------:|------------------------------:|
| SELECT 1 row by PK    |                       2.4x |                          5.1x |
```

The two ratios identify where to focus:
- **Exposed-R2DBC vs Raw-R2DBC** — how much Exposed adds to the driver. High ratio = Exposed overhead, fixable.
- **Exposed-R2DBC vs Exposed-JDBC** — the user-visible gap.

If `Exposed-R2DBC vs Raw-R2DBC` is ~1.0x, the gap is inherent to R2DBC and Phase 2 stops. If ≫1.0x, we have headroom.

### JMH micro-benchmark table

```markdown
| Benchmark                          | Mode | ns/op | B/op |
|------------------------------------|------|------:|-----:|
| getMatchingMappers (IntColumnType) | avgt |   180 |  240 |
| getValue       (IntColumnType)     | avgt |   320 |  280 |
```

## Files to create

1. **`exposed-perf-tests/build.gradle.kts`** — JMH gradle plugin (`me.champeau.jmh`), application plugin, dependencies on `exposed-jdbc`, `exposed-r2dbc`, H2 driver, R2DBC H2 driver
2. **`exposed-perf-tests/src/jmh/kotlin/.../TypeMapperResolutionBenchmark.kt`**
3. **`exposed-perf-tests/src/jmh/kotlin/.../TypeMapperAllocationBenchmark.kt`**
4. **`exposed-perf-tests/src/jmh/kotlin/.../MapperPropertyAccessBenchmark.kt`**
5. **`exposed-perf-tests/src/main/kotlin/.../Main.kt`** — entry point; orchestrates scenarios and backends
6. **`exposed-perf-tests/src/main/kotlin/.../scenarios/SelectByPk.kt`** — four backend implementations of SELECT-1
7. **`exposed-perf-tests/src/main/kotlin/.../scenarios/SelectMany.kt`** — four backend implementations of SELECT-100 / SELECT-1000
8. **`exposed-perf-tests/src/main/kotlin/.../scenarios/InsertSingle.kt`**
9. **`exposed-perf-tests/src/main/kotlin/.../scenarios/InsertBatch.kt`**
10. **`exposed-perf-tests/src/main/kotlin/.../scenarios/UpdateSingle.kt`**
11. **`exposed-perf-tests/src/main/kotlin/.../report/MarkdownReport.kt`** — formats output as the markdown tables above
12. **`exposed-perf-tests/PROFILING.md`** — async-profiler attach commands, JFR usage, what to look for in flame graphs
13. **`exposed-perf-tests/benchmark-results.md`** — captured baseline numbers + written assessment of top hotspots

## Definition of done

- `./gradlew :exposed-perf-tests:run` produces a clean markdown report
- `./gradlew :exposed-perf-tests:jmh` runs without errors and produces JMH output
- `benchmark-results.md` contains numbers from a clean run on the dev machine
- `PROFILING.md` is verified — anyone following it gets a usable flame graph
- `benchmark-results.md` ends with a written assessment of the top 3 hotspots (identified from profile + JMH) that Phase 2 should target

That assessment is the input to Phase 2's design doc.

## Testing approach

Standard correctness checks: the app must produce the same row counts across all four backends. Divergent results = bug in test setup, not perf signal. The scenarios assert on row counts before recording any timing.

No new unit tests needed; the perf module isn't part of the regular test suite (`./gradlew test_h2_v2` does not include it).

## Risks

- **JMH visibility into private `getMatchingMappers`**: We need to call it directly. Options: reflection (slow but works in micro-bench because cost is amortized over warmup), or making it `internal` for testability. The latter is a one-line code change; we'll prefer it if it's clean. If reflection is needed, document the warmup behavior so readers understand benchmark numbers aren't skewed.
- **H2 R2DBC differences from production drivers**: H2 in-memory can hide async-driver overhead present in real Postgres/MySQL R2DBC drivers. We accept this risk for Phase 1 because the issue is about Exposed-layer overhead, not driver performance. Phase 2 may add a Postgres scenario if needed.
- **Single-machine measurement noise**: `ns/op` numbers will vary across runs by 5-15%. Reporting medians over 10 iterations and committing actual numbers (not aspirational) addresses this.

## What's NOT in Phase 1

- No changes to `exposed-r2dbc` source code
- No changes to `exposed-core` or other modules
- No CI integration (perf benchmarks are run-on-demand)
- No optimization work — that's Phase 2
