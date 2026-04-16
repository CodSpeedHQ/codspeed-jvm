import org.gradle.internal.os.OperatingSystem
import org.gradle.language.cpp.tasks.CppCompile
import org.gradle.nativeplatform.tasks.LinkSharedLibrary

plugins {
    id("cpp-library")
}

val isLinux = OperatingSystem.current().isLinux

val javaHome = System.getenv("JAVA_HOME") ?: System.getProperty("java.home")
val jdkHome = if (File(javaHome).name == "jre") File("$javaHome/..").canonicalPath else javaHome

library {
    baseName.set("instrument_hooks_jni")
    linkage.set(listOf(Linkage.SHARED))
    targetMachines.set(listOf(machines.linux.x86_64))
}

tasks.withType<CppCompile>().configureEach {
    dependsOn(":jmh-core:generateJniHeaders")
    // cpp-library's source set filters to *.cpp/*.cc/*.c++; add .c files directly
    // on the compile task to bypass that filter. g++ detects .c extension and
    // invokes its C frontend, so -std=c11 is accepted.
    source(fileTree("src/main/c") { include("*.c") })
    source(fileTree("instrument-hooks/dist") { include("*.c") })
    // -x c forces C mode; g++ otherwise compiles .c files as C++ (strict static-const rules, etc.)
    compilerArgs.addAll(listOf("-x", "c", "-std=c11", "-O3", "-Wno-format", "-Wno-format-security"))
    includes.from(
        "instrument-hooks/includes",
        project(":jmh-core").layout.buildDirectory.dir("generated/jni-headers"),
        "$jdkHome/include",
        "$jdkHome/include/linux",
    )
    onlyIf { isLinux }
}

tasks.withType<LinkSharedLibrary>().configureEach {
    linkerArgs.addAll(listOf("-lpthread", "-ldl"))
    onlyIf { isLinux }
}

// Skip debug variant — we only ship release.
tasks.matching { it.name.contains("Debug") }.configureEach { onlyIf { false } }
