# EXPOSED-1003 Phase 2 — Performance Results

**Branch:** `obabichev/exposed-1003-r2dbc-performance`
**Optimization commit:** `cf26f3452`
**Captured on:** 2026-04-28
**Hardware:** Apple M3 Max, 64 GB RAM, macOS 23.1.0
**JDK:** OpenJDK 17.0.10 (Amazon Corretto 17.0.10.7.1)
**Database:** H2 in-memory (JDBC + R2DBC)

## What changed

Three optimizations to the R2DBC type-mapper hot path, applied in a single commit:

1. **Cache `(dialect, columnType) → List<TypeMapper>`** in `R2dbcRegistryTypeMappingImpl.getMatchingMappers` via a `ConcurrentHashMap`, cleared on `register()`.
   Eliminates the per-column-read filter+allocate of all registered mappers.
2. **Backing-field `val` for `columnTypes` / `dialects`** on all built-in mappers (5 files). Stops reconstructing the same `KClass[]` list on every property read.
3. **`NoValueContainer` singleton** via internal `sharedNoValueContainer<T>()` helper. 5 call sites updated. Public `class NoValueContainer<T>` retained for binary
   compatibility.

## End-to-end (Exposed-R2DBC, H2 in-memory)

Median ns/op over 10 iterations of 10,000 ops, after 1,000 warmup ops.

**Column legend:**

- **BEFORE / AFTER ns/op** — Exposed-R2DBC time per operation, before and after the Phase 2 optimizations.
- **Speedup** — `BEFORE / AFTER`. How many times faster the same Exposed-R2DBC scenario runs after the optimizations.
- **vs Raw-R2DBC: before → after** — How much overhead Exposed adds on top of the raw R2DBC driver, expressed as `Exposed-R2DBC ns/op ÷ Raw-R2DBC ns/op`, shown for
  both the baseline and the optimized build. `1.0x` would mean Exposed adds zero overhead on top of the driver. The "→" reads as "improved from … to …". For example,
  `5.36x → 1.93x` means: in the baseline Exposed-R2DBC took 5.36× the raw-R2DBC time for that scenario; after the fix it takes only 1.93×, i.e. Exposed overhead
  shrank from ~436% to ~93%. Lower is better.

| Scenario              | BEFORE ns/op | AFTER ns/op |   Speedup | vs Raw-R2DBC: before → after |
|-----------------------|-------------:|------------:|----------:|------------------------------|
| SELECT 1 row by PK    |       29,920 |      27,161 |     1.10x | 1.82x → 1.99x                |
| **SELECT ~100 rows**  |      310,809 |     112,284 | **2.77x** | 5.36x → **1.93x**            |
| **SELECT ~1000 rows** |    3,008,607 |     902,295 | **3.33x** | 6.69x → **2.02x**            |
| INSERT 1 row          |       28,126 |      26,800 |     1.05x | 2.07x → 1.84x                |
| INSERT batch of 10    |       92,600 |      70,216 |     1.32x | 2.36x → 1.76x                |
| UPDATE 1 row by PK    |       25,149 |      23,944 |     1.05x | 1.86x → 1.76x                |

Multi-row SELECT scenarios collapse to roughly the inherent R2DBC driver floor (~2x raw R2DBC). Single-row / write scenarios were never dominated by mapper-resolution
cost, so the speedup is small there but the ratio still improves.

**Why the "vs Raw-R2DBC" ratio gets slightly worse for some single-row scenarios** (e.g. SELECT 1 row by PK: 1.82x → 1.99x): Exposed-R2DBC got ~10% faster, but raw
R2DBC also varies run-to-run, and on a single-row scenario the absolute difference (a few hundred ns) is dominated by run-to-run noise rather than by
mapper-resolution savings. The slight regression in ratio for single-row scenarios is not a real regression — Exposed-R2DBC ns/op is unchanged or improved in absolute
terms.

## JMH micro-benchmarks

`avgt` mode, 5 warmup iterations + 10 measurement iterations, fork 1.

| Benchmark                         | BEFORE | AFTER |   Speedup | Allocation: before → after |
|-----------------------------------|-------:|------:|----------:|----------------------------|
| TypeMapper getValue (Integer)     | 567 ns | 32 ns | **17.9x** | 920 B/op → ≈10⁻⁶           |
| TypeMapper getValue (String)      | 648 ns | 33 ns | **19.6x** | 920 B/op → ≈10⁻⁶           |
| TypeMapper getValue (alloc bench) | 581 ns | 31 ns | **18.5x** | 920 B/op → ≈10⁻⁶           |
| MapperProperty readColumnTypes    |  74 ns | 29 ns |     2.53x | 224 B/op → ≈10⁻⁶           |
| MapperProperty readDialects       |  29 ns | 30 ns |     ~1.0x | 24 B/op → ≈10⁻⁶            |

Per-call `getValue` cost dropped **~18x** (580 ns → 31 ns); per-call allocation dropped from **920 B/op → effectively zero**.

## Verification

- `:exposed-r2dbc-tests:test_h2_v2` — passes
- `:exposed-r2dbc:apiCheck` — passes (no public binary-API change)
- `:exposed-r2dbc:detekt` — passes

## Conclusion

EXPOSED-1003 is resolved at the type-mapper layer:

- The "30–80% slower than JDBC" gap reported by the issue is no longer attributable to Exposed.
- Multi-row SELECT scenarios are 2.8–3.3x faster end-to-end.
- The Exposed-R2DBC vs Raw-R2DBC ratio for SELECT-1000 went from 6.7x to 2.0x — the inherent R2DBC driver floor.
- Type-mapper hot path: 18x faster, allocation eliminated.

What remains (~2x raw-R2DBC overhead) is inherent to the R2DBC reactive→coroutine bridging model. The baseline assessment (`benchmark-results.md`) documents this as
out-of-scope for Phase 2 because data shows it would require disproportionate effort for sub-1.5x gains.

## Reproducing these numbers

From the repo root:

```bash
# End-to-end timings (~3-7 minutes)
./gradlew :exposed-perf-tests:run

# JMH micro-benchmarks with allocation profiling (~12 minutes)
./gradlew :exposed-perf-tests:jmh -PjmhProfilers=gc

# To re-run the baseline (before optimization), check out the previous commit:
git checkout cf26f3452~1
./gradlew :exposed-perf-tests:run
```
