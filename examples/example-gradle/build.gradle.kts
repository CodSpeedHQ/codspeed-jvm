plugins {
    java
    id("me.champeau.jmh") version "0.7.2"
}

group = "io.codspeed"
version = "1.0-SNAPSHOT"

jmh {
    jmhVersion.set("0.1.0-alpha")

    benchmarkMode.set(listOf("avgt"))
    timeUnit.set("ns")
    warmupIterations.set(2)
    warmup.set("1s")
    iterations.set(3)
    timeOnIteration.set("1s")
    fork.set(3)
    resultFormat.set("JSON")

    // Force a System.gc() between iterations so heap state is reset before each
    // measurement starts. Without this, a GC pause can land inside the measurement
    // window and show up as a spurious regression in CI.
    forceGC = true

    // Run subset of the benchmarks to avoid long CI runtimes
    if (System.getenv("CODSPEED_ENV") != null) {
        logger.lifecycle("CODSPEED_ENV detected — running curated CI benchmark subset")
        // me.champeau.jmh joins `includes` with commas into a single positional
        // regex passed to JMH, so multiple entries collapse to one pattern with
        // literal commas and match nothing. Use a single alternation instead.
        includes.set(listOf(
            ".*(SleepBenchmark|BacktrackingBenchmark|FibBenchmark).*",
        ))
    }
}

sourceSets {
    named("jmh") {
        java {
            srcDir("../TheAlgorithms-Java/src/main/java")
        }
    }
}

dependencies {
    jmh("org.apache.commons:commons-lang3:3.20.0")
    jmh("org.apache.commons:commons-collections4:4.5.0")
}
