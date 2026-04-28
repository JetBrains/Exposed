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
