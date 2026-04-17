plugins {
    java
    id("me.champeau.jmh") version "0.7.2"
}

group = "io.codspeed"
version = "1.0-SNAPSHOT"

jmh {
    jmhVersion.set("0.1.0")

    // Force a System.gc() between iterations so heap state is reset before each
    // measurement starts. Without this, a GC pause can land inside the measurement
    // window and show up as a spurious regression in CI.
    forceGC = true
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
