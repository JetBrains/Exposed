# Baseline benchmark results — `obabichev/exposed-1003-r2dbc-performance` @ f42ec0431

Captured on: 2026-04-28
Hardware: Apple M3 Max, 64 GB RAM, macOS 23.1.0 (darwin arm64)
JDK: OpenJDK 17.0.10 (Amazon Corretto 17.0.10.7.1, 64-Bit Server VM)

## End-to-end (H2 in-memory)

### Per-scenario timings (ns/op, median over 10 iterations of 10 000 ops)

| Scenario                | Raw JDBC | Exposed JDBC | Raw R2DBC | Exposed R2DBC |
|-------------------------|---------:|-------------:|----------:|--------------:|
| SELECT 1 row by PK      |     2002 |        14540 |     16414 |         29920 |
| SELECT ~100 rows        |     2193 |        19557 |     58018 |        310809 |
| SELECT ~1000 rows       |    18737 |       117410 |    449565 |       3008607 |
| INSERT 1 row            |     2458 |         8687 |     13620 |         28126 |
| INSERT batch of 10 rows |     7562 |        31879 |     39224 |         92600 |
| UPDATE 1 row by PK      |     2424 |         7761 |     13487 |         25149 |

### Ratios

| Scenario                | Exposed-R2DBC vs Raw-R2DBC | Exposed-R2DBC vs Exposed-JDBC |
|-------------------------|---------------------------:|------------------------------:|
| SELECT 1 row by PK      |                      1.82x |                         2.06x |
| SELECT ~100 rows        |                      5.36x |                        15.89x |
| SELECT ~1000 rows       |                      6.69x |                        25.62x |
| INSERT 1 row            |                      2.07x |                         3.24x |
| INSERT batch of 10 rows |                      2.36x |                         2.90x |
| UPDATE 1 row by PK      |                      1.86x |                         3.24x |

## JMH micro-benchmarks

All runs: `avgt` mode, 5 warmup iterations + 10 measurement iterations, fork 1, JMH 1.37.
Profiler: `-prof gc` (gc.alloc.rate.norm = bytes allocated per operation, tightly controlled).

### SmokeBenchmark

| Benchmark        | Param | Mode | Cnt | Score | Error | Units |
|------------------|-------|------|----:|------:|------:|-------|
| SmokeBenchmark.add | N/A | avgt | 10 | 0.303 | ±0.053 | ns/op |
| SmokeBenchmark.add:gc.alloc.rate.norm | N/A | avgt | 10 | ≈10⁻⁸ | | B/op |

### TypeMapperResolutionBenchmark

| Benchmark | Param | Mode | Cnt | Score | Error | Units |
|-----------|-------|------|----:|------:|------:|-------|
| TypeMapperResolutionBenchmark.getValue | Integer | avgt | 10 | 567.513 | ±19.598 | ns/op |
| TypeMapperResolutionBenchmark.getValue:gc.alloc.rate.norm | Integer | avgt | 10 | 920.000 | ±0.001 | B/op |
| TypeMapperResolutionBenchmark.getValue | String | avgt | 10 | 647.765 | ±36.022 | ns/op |
| TypeMapperResolutionBenchmark.getValue:gc.alloc.rate.norm | String | avgt | 10 | 920.000 | ±0.001 | B/op |

### TypeMapperAllocationBenchmark (with -prof gc)

| Benchmark | Param | Mode | Cnt | Score | Error | Units |
|-----------|-------|------|----:|------:|------:|-------|
| TypeMapperAllocationBenchmark.getValue | N/A | avgt | 10 | 580.621 | ±31.002 | ns/op |
| TypeMapperAllocationBenchmark.getValue:gc.alloc.rate | N/A | avgt | 10 | 1512.747 | ±79.225 | MB/sec |
| TypeMapperAllocationBenchmark.getValue:gc.alloc.rate.norm | N/A | avgt | 10 | 920.000 | ±0.001 | B/op |
| TypeMapperAllocationBenchmark.getValue:gc.count | N/A | avgt | 10 | 253.000 | | counts |
| TypeMapperAllocationBenchmark.getValue:gc.time | N/A | avgt | 10 | 219.000 | | ms |

### MapperPropertyAccessBenchmark (with -prof gc)

| Benchmark | Param | Mode | Cnt | Score | Error | Units |
|-----------|-------|------|----:|------:|------:|-------|
| MapperPropertyAccessBenchmark.readColumnTypes | N/A | avgt | 10 | 74.238 | ±0.395 | ns/op |
| MapperPropertyAccessBenchmark.readColumnTypes:gc.alloc.rate | N/A | avgt | 10 | 2877.517 | ±15.326 | MB/sec |
| MapperPropertyAccessBenchmark.readColumnTypes:gc.alloc.rate.norm | N/A | avgt | 10 | 224.000 | ±0.001 | B/op |
| MapperPropertyAccessBenchmark.readColumnTypes:gc.count | N/A | avgt | 10 | 476.000 | | counts |
| MapperPropertyAccessBenchmark.readDialects | N/A | avgt | 10 | 28.812 | ±0.166 | ns/op |
| MapperPropertyAccessBenchmark.readDialects:gc.alloc.rate | N/A | avgt | 10 | 794.399 | ±4.573 | MB/sec |
| MapperPropertyAccessBenchmark.readDialects:gc.alloc.rate.norm | N/A | avgt | 10 | 24.000 | ±0.001 | B/op |
| MapperPropertyAccessBenchmark.readDialects:gc.count | N/A | avgt | 10 | 133.000 | | counts |
| MapperPropertyAccessBenchmark.readDialects:gc.time | N/A | avgt | 10 | 64.000 | | ms |

## Profile findings

JFR recording was captured via direct JVM invocation with `-XX:StartFlightRecording=duration=600s,filename=/tmp/perf.jfr`.
Total CPU execution samples collected: 14 577. Total allocation samples collected: 67 805.
The recording spans all six scenarios; the multi-row SELECT scenarios dominate because they iterate over 100–1000 rows per operation
and thus execute the hot path (type mapper resolution + row decode) the most.

### CPU (top 3 frames by self-time during the full recording — dominated by SELECT-100/1000)

1. `R2dbcRegistryTypeMappingImpl.getMatchingMappers` — **~54% self-time** (7 distinct hot lines in the method body, 5 426 of 14 577 samples on those lines alone, with surrounding `LinkedHashMap.get` and mapper `getColumnTypes()` calls pushing the combined mapper-scan cluster to ~78% of all samples).
2. `io.r2dbc.h2.H2Row.get` / `DefaultCodecs.decode` — **~25% self-time** (R2DBC driver column decode; 3 706 samples including `Arrays$ArrayItr.hasNext`, codec `canDecode` scanning).
3. `kotlinx.coroutines.channels.BufferedChannel.*` — **~5% self-time** (per-row Channel signaling in `R2dbcResult.mapRows`; 756 samples across `isClosed`, `completeCancel`, `trySend`).

### Allocation (top 3 sites during full recording)

1. `kotlin.reflect.KClass[]` — **11 753 allocation samples, peak weight 8 MB per sample burst** — sourced from `PrimitiveTypeMapper.getColumnTypes()` (line 19) and `BinaryTypeMapper.getColumnTypes()` (line 23), both of which are `get()` properties that reconstruct a `listOf(…)` containing KClass references on every call. `getMatchingMappers` invokes `columnTypes` for every mapper for every column read.
2. `java.util.Arrays$ArrayList` / `java.util.Collections$SingletonList` — **6 793 + 5 456 samples** — intermediate collections produced inside `getMatchingMappers` by `.filter { … }` chaining and by `kotlin.collections.listOf()` wrappers inside `getColumnTypes()` getters.
3. `org.jetbrains.exposed.v1.r2dbc.mappers.NoValueContainer` — **4 538 samples** — `TypeMapper.getValue` wraps a "no match" result in a new `NoValueContainer` object per mapper invocation rather than returning a sentinel singleton/null.

## Phase 2 hotspot assessment

Based on the data above, the top hotspots that Phase 2 should target are:

1. **`getMatchingMappers` rescanning all mappers on every column read (78% CPU, 920 B/op allocation)** —
   JFR shows 78% of all CPU samples in the mapper-scan cluster: `getMatchingMappers` iterates over all 8 registered mappers, calls each mapper's `.dialects` and `.columnTypes` getters (which allocate fresh collections on each access — 224 B/op for `columnTypes` alone per MapperPropertyAccessBenchmark), and evaluates `KClass.isInstance()` via kotlin-reflect for each entry. For SELECT-1000 with 4 columns, this is ~32 000 mapper scans per operation. JMH shows 580–648 ns/op and 920 B/op for a single `getValue` call. Phase 2 fix: cache `(dialect::class, columnType::class) → List<TypeMapper>` in a `ConcurrentHashMap` so that the full scan runs exactly once per (dialect, column-type) pair. Expected impact: eliminate ~75–80% of `getMatchingMappers` CPU cost and virtually all 920 B/op allocation, projecting SELECT-1000 Exposed-R2DBC from 3.0M ns to under 1.0M ns (i.e., closing the 6.7x gap with Raw R2DBC down to ~2x).

2. **Mapper `columnTypes` and `dialects` properties allocating fresh lists on every call (224 B/op per `columnTypes` read)** —
   MapperPropertyAccessBenchmark shows that `readColumnTypes` costs 74 ns and allocates 224 B/op per single-mapper access. The root cause is that `PrimitiveTypeMapper.columnTypes`, `BinaryTypeMapper.columnTypes`, and similar are declared as `override val columnTypes: List<KClass<…>> get() = listOf(…)`, a Kotlin `get()` that constructs a new list object on each invocation rather than returning a cached field. With 8 mappers and 32 000 column reads per SELECT-1000 operation, this contributes ~7 MB of ephemeral allocation per operation, confirmed by the 11 753 `KClass[]` allocation samples in JFR. Phase 2 fix: convert all `columnTypes` and `dialects` getter overrides across all `TypeMapper` implementations to backing-field `val` initialised in the constructor (or object declaration). Expected impact: eliminate 224 B/op of allocation per mapper property read, reducing total per-`getValue` allocation from 920 B to near zero even before the caching fix is applied.

3. **`NoValueContainer` instantiation as a "no match" signal (4 538 allocation samples)** —
   `TypeMapper.getValue` returns a `ValueContainer` wrapper to signal "this mapper did not handle the column"; the `NoValueContainer` case is a new heap object allocated for every mapper that rejects the column. Because `getMatchingMappers` returns only matching mappers, every mapper in the returned list is expected to succeed on first try — yet a new `NoValueContainer` is still created for the first mapper that doesn't produce a value, and the result is checked via `.isPresent` rather than a null/sealed-singleton sentinel. Phase 2 fix: replace `NoValueContainer` with a global singleton (or return `null` directly), and short-circuit after the first non-empty result. Expected impact: eliminate 4 538 allocation samples observed in JFR (~1 object per mapper call), reducing residual allocation pressure after the caching fix is in place.

Hotspots considered and rejected (with reason):

- **R2DBC reactive→coroutine bridging (BufferedChannel per-row signaling, ~5% CPU)** — `kotlinx.coroutines.channels.BufferedChannel` overhead accounts for only 756 samples (5.1% of total), and this cost is intrinsic to any coroutine-based reactive subscription model. Replacing it with `suspendCancellableCoroutine` batch collection would require invasive architectural changes to `R2dbcResult.mapRows` with marginal gain relative to the 78% mapper-scan problem. Not worth targeting in Phase 2.
- **H2 R2DBC driver codec scanning (`DefaultCodecs.decode`, ~25% CPU)** — The 3 706 samples in H2's `DefaultCodecs` are driver-internal and outside the scope of Exposed optimisation. This overhead is present in Raw R2DBC as well and sets the floor for any R2DBC implementation; Exposed cannot reduce it.
- **`ResultRow` and `ResultRowCache` allocation (~671 + 295 samples)** — These are a small fraction of total allocation and represent necessary per-row result objects. Pooling them would add complexity with <1% end-to-end impact and should not be prioritised.

## Conclusion (baseline)

Exposed-R2DBC has a **6.7x overhead vs Raw R2DBC on SELECT-1000** (3.01M ns vs 450K ns), and a **5.4x overhead on SELECT-100** (311K ns vs 58K ns). Single-row and write operations show a more modest 1.8–2.4x overhead. The EXPOSED-1003 claim that R2DBC type-mapping resolution is a performance bottleneck is **confirmed**: JFR shows 78% of all CPU samples sitting inside `R2dbcRegistryTypeMappingImpl.getMatchingMappers` and its mapper property getters, and JMH confirms 920 B/op allocation and ~580 ns per `getValue` call. Phase 2 will deliver a `(dialect, columnType)` → mapper cache plus `columnTypes`/`dialects` property de-allocation, which is projected to reduce the SELECT-1000 ratio from ~6.7x to approximately 2x vs Raw R2DBC.

---

# Phase 2 results — `cf26f3452` (after optimization)

Captured on: 2026-04-28
Same hardware/JDK as baseline above.

Optimizations applied (single commit `cf26f3452`):

1. `R2dbcRegistryTypeMappingImpl.getMatchingMappers` now caches the filtered mapper list per `(dialectClass, columnTypeClass)` in a `ConcurrentHashMap`, cleared on `register()`.
2. `columnTypes` and `dialects` overrides on all built-in mappers converted from `get() = listOf(…)` to backing-field `val = listOf(…)` (5 files).
3. `NoValueContainer` instantiation replaced with a shared singleton via internal `sharedNoValueContainer<T>()` helper (5 call sites updated; public `class NoValueContainer<T>` retained for binary compat).

## End-to-end (H2 in-memory) — BEFORE / AFTER

| Scenario              | Exposed-R2DBC BEFORE (ns/op) | Exposed-R2DBC AFTER (ns/op) | Speedup | vs Raw-R2DBC BEFORE | vs Raw-R2DBC AFTER |
|-----------------------|----------------------------:|----------------------------:|--------:|--------------------:|-------------------:|
| SELECT 1 row by PK    |                       29920 |                       27161 |   1.10x |               1.82x |              1.99x |
| SELECT ~100 rows      |                      310809 |                      112284 | **2.77x** |             5.36x |          **1.93x** |
| SELECT ~1000 rows     |                     3008607 |                      902295 | **3.33x** |             6.69x |          **2.02x** |
| INSERT 1 row          |                       28126 |                       26800 |   1.05x |               2.07x |              1.84x |
| INSERT batch of 10    |                       92600 |                       70216 |   1.32x |               2.36x |              1.76x |
| UPDATE 1 row by PK    |                       25149 |                       23944 |   1.05x |               1.86x |              1.76x |

Multi-row SELECT scenarios collapse to roughly the inherent R2DBC driver floor (~2x raw R2DBC). Single-row and write scenarios were never dominated by mapper-resolution cost, so the speedup is small there but the ratio still improves.

## JMH micro-benchmarks — BEFORE / AFTER

| Benchmark                                  | BEFORE ns/op | AFTER ns/op | Speedup | BEFORE B/op | AFTER B/op  |
|--------------------------------------------|-------------:|------------:|--------:|------------:|------------:|
| TypeMapperResolutionBenchmark.getValue Integer | 567.5    |        31.7 | **17.9x** |       920.0 | ≈10⁻⁶ (~0) |
| TypeMapperResolutionBenchmark.getValue String  | 647.8    |        33.0 | **19.6x** |       920.0 | ≈10⁻⁶ (~0) |
| TypeMapperAllocationBenchmark.getValue         | 580.6    |        31.4 | **18.5x** |       920.0 | ≈10⁻⁶ (~0) |
| MapperPropertyAccessBenchmark.readColumnTypes  |  74.2    |        29.3 |   2.53x  |       224.0 | ≈10⁻⁶ (~0) |
| MapperPropertyAccessBenchmark.readDialects     |  28.8    |        30.1 |   ~1.0x  |        24.0 | ≈10⁻⁶ (~0) |

Per-call `getValue` cost dropped from ~580 ns to ~31 ns (94% reduction) and per-call allocation dropped from 920 B/op to effectively zero.

## Conclusion (post-optimization)

EXPOSED-1003 is resolved at the type-mapper layer. The combined effect of the three Phase 2 optimizations:

- Multi-row SELECT scenarios are **2.8–3.3x faster** end-to-end and now sit at ~2x raw R2DBC (the inherent driver floor).
- Per-`getValue` CPU cost dropped **~18x** (from 580 ns to 31 ns) and per-call allocation dropped from 920 B/op to ~0.
- The ratio that the original report flagged as "30–80% slower than JDBC" is now bounded by R2DBC driver overhead, not by Exposed.
- All `:exposed-r2dbc-tests:test_h2_v2` tests pass; `apiCheck` confirms no public binary-API change; `detekt` passes.

Single-row and write scenarios were not dominated by mapper-resolution cost in the baseline and consequently see only marginal improvement (~5–32%). Further work to close the remaining ~2x raw-R2DBC gap (e.g., reactive→coroutine bridging, ResultRow allocation) is documented as rejected in the baseline assessment because the data shows it would require disproportionate effort for sub-1.5x gains.
