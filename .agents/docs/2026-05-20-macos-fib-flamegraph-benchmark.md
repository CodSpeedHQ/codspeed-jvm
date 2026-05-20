# Proposal: macOS-only Fib benchmark for flamegraph testing

## Goal

Add a benchmark whose only purpose is to exercise CodSpeed's flamegraph
symbolization pipeline on macOS (codspeed-macro runners). It should:

- run on macOS (locally and on `codspeed-macro` CI)
- be **skipped** on Linux + Windows so it doesn't pollute the regular walltime
  results or fail on platforms where the flamegraph path isn't applicable
- have a stack shape that produces an obviously-recognisable flamegraph (deep
  recursion = `fib`)

## Findings

- `examples/example-gradle/src/jmh/java/bench/FibBenchmark.java` already
  defines a recursive `fib(30)` benchmark. Reusing the same shape (but a
  dedicated class) keeps the flamegraph trivially recognisable.
- The Gradle JMH plugin (`me.champeau.jmh` v0.7.2) exposes `includes` /
  `excludes` regex filters. We're already using `includes` to curate the CI
  subset in `examples/example-gradle/build.gradle.kts:32`.
- CI is matrixed in `.github/workflows/ci.yml`. The flamegraph-relevant job
  is `walltime-benchmarks` on `codspeed-macro` — macOS-only by definition.
  The cross-platform jobs (`build-and-run-gradle`, `build-and-run-maven`) run
  the JMH JAR on Linux + Windows + macOS, so a naive new benchmark would also
  execute there.
- JMH itself has no `@SkipOnPlatform`-style annotation. The two reasonable
  gating mechanisms are:
  1. Filter at the runner level (Gradle `excludes`)
  2. Throw / `Assume.assumeTrue(...)` inside `@Setup` (JMH treats a thrown
     exception in setup as a benchmark error, not a skip — so this is
     **not** suitable).

## Options

### 1. Gate via Gradle `excludes` based on `OperatingSystem`  (recommended)

Add `FibFlamegraphBenchmark` next to the existing `FibBenchmark`, and in
`examples/example-gradle/build.gradle.kts` exclude it everywhere except macOS:

```kotlin
import org.gradle.internal.os.OperatingSystem

jmh {
    // …existing config…
    if (!OperatingSystem.current().isMacOsX) {
        excludes.add(".*FibFlamegraphBenchmark.*")
    }
}
```

The `CODSPEED_ENV` branch already filters down to a curated set on CI; add
`FibFlamegraphBenchmark` to that alternation so it runs on `codspeed-macro`
but stays out of the way on the regular Linux/Windows matrix.

Pros:
- One source of truth (the build script). No per-benchmark scaffolding.
- `OperatingSystem.current()` is the same API Gradle uses internally and
  doesn't require a new dependency.
- Easy to mirror in the Maven example (`<profile><activation><os>` block).

Cons:
- Excludes are regex-based; the benchmark class name must stay matchable.
  Mitigation: keep `Flamegraph` in the class name and exclude on that token.

### 2. Put macOS-only benchmarks in a dedicated package + filter

E.g. `bench.macos.FibFlamegraphBenchmark`, then exclude `bench\\.macos\\..*`
when `!isMacOsX`.

Pros: groups future macOS-only benchmarks naturally; no per-class regex.
Cons: more moving parts up front for a single benchmark.

### 3. Use a System property check + early `return`

Make the benchmark body a no-op on non-macOS. JMH still runs it, so the run
shows up in results as a near-zero benchmark.

Pros: no build-script changes.
Cons: pollutes results, defeats the "skip on Linux" goal. **Rejected.**

## Recommendation

Go with **Option 1**. Smallest diff, no new package, and the gating lives in
the same place as the existing `CODSPEED_ENV` filter so future readers
discover it immediately.

### Concrete change set (not yet applied)

1. New file `examples/example-gradle/src/jmh/java/bench/FibFlamegraphBenchmark.java`
   — copy of `FibBenchmark` with a distinct class name and a `@Param` value
   that pushes recursion deep enough to be visually interesting in the
   flamegraph (e.g. `35`).
2. Edit `examples/example-gradle/build.gradle.kts`:
   - import `org.gradle.internal.os.OperatingSystem`
   - add `excludes.add(".*FibFlamegraphBenchmark.*")` under
     `if (!OperatingSystem.current().isMacOsX)`
   - extend the `CODSPEED_ENV` include alternation to include
     `FibFlamegraphBenchmark`
3. No CI changes needed: the existing `walltime-benchmarks` job on
   `codspeed-macro` will pick it up automatically.

## Open questions

- Should the Maven example mirror this? The Maven example doesn't currently
  run under `codspeed-macro`, so probably no — but worth confirming.
- Do we want to lock the `@Fork` / iteration counts down for the flamegraph
  benchmark specifically (a single long iteration produces a cleaner
  flamegraph than many short ones)?
