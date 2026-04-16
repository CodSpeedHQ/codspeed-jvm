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
    baseName.set("perf_map_agent")
    linkage.set(listOf(Linkage.SHARED))
    targetMachines.set(listOf(machines.linux.x86_64))
}

tasks.withType<CppCompile>().configureEach {
    // cpp-library's source set filters to *.cpp/*.cc/*.c++; add .c files directly
    // on the compile task to bypass that filter. g++ detects .c extension and
    // invokes its C frontend, so -std=c11 is accepted.
    source(fileTree("src/main/c") { include("*.c") })
    // -x c forces C mode; g++ otherwise compiles .c files as C++ (strict static-const rules, etc.)
    compilerArgs.addAll(listOf("-x", "c", "-std=c11", "-O3"))
    includes.from("$jdkHome/include", "$jdkHome/include/linux")
    onlyIf { isLinux }
}

tasks.withType<LinkSharedLibrary>().configureEach {
    linkerArgs.addAll(listOf("-lpthread"))
    onlyIf { isLinux }
}

// Skip debug variant — we only ship release.
tasks.matching { it.name.contains("Debug") }.configureEach { onlyIf { false } }
