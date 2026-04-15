import java.text.SimpleDateFormat
import java.util.Date

val isLinux = org.gradle.internal.os.OperatingSystem.current().isLinux

val nativeDir = file("src/main/java/io/codspeed/instrument_hooks/c")
val instrumentHooksDir = file("src/main/java/io/codspeed/instrument_hooks/instrument-hooks")
val perfMapAgentDir = file("src/main/java/io/codspeed/perf_map_agent/c")
val jniHeaderDir = file("${layout.buildDirectory.get()}/generated/jni-headers")
val nativeLibDir = layout.buildDirectory.dir("native")

dependencies {
    api("net.sf.jopt-simple:jopt-simple:5.0.4")
    api("org.apache.commons:commons-math3:3.6.1")
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("junit:junit:4.13.2")
}

tasks.compileJava {
    options.compilerArgs.addAll(listOf("-proc:none", "-Xlint:serial"))
}

tasks.register<JavaCompile>("generateJniHeaders") {
    description = "Generate JNI headers from native method declarations"
    source = sourceSets.main.get().java
    classpath = sourceSets.main.get().compileClasspath
    destinationDirectory.set(file("${layout.buildDirectory.get()}/classes/jni"))
    options.compilerArgs.addAll(listOf("-h", jniHeaderDir.absolutePath))
    onlyIf { isLinux }
}

tasks.register<Exec>("compileNative") {
    description = "Compile JNI native library"
    dependsOn("generateJniHeaders")
    onlyIf { isLinux }

    val javaHome = System.getenv("JAVA_HOME") ?: System.getProperty("java.home")
    val jdkHome = if (File(javaHome).name == "jre") File("$javaHome/..").canonicalPath else javaHome
    val outputLib = nativeLibDir.get().file("libinstrument_hooks_jni.so").asFile

    inputs.files(fileTree(nativeDir) { include("*.c") })
    inputs.files(fileTree("${instrumentHooksDir}/dist") { include("*.c") })
    inputs.dir(jniHeaderDir)
    outputs.file(outputLib)

    commandLine(
        "gcc",
        "-shared", "-fPIC", "-O2", "-std=c11",
        "-Wno-format", "-Wno-format-security",
        "-o", outputLib.absolutePath,
        "${nativeDir}/instrument_hooks_jni.c",
        "${instrumentHooksDir}/dist/core.c",
        "-I${instrumentHooksDir}/includes",
        "-I${jniHeaderDir}",
        "-I${jdkHome}/include",
        "-I${jdkHome}/include/linux",
        "-lpthread", "-ldl"
    )
}

tasks.register<Exec>("compilePerfMapAgent") {
    description = "Compile perf-map JVMTI agent"
    onlyIf { isLinux }

    val javaHome = System.getenv("JAVA_HOME") ?: System.getProperty("java.home")
    val jdkHome = if (File(javaHome).name == "jre") File("$javaHome/..").canonicalPath else javaHome
    val outputLib = nativeLibDir.get().file("libperf_map_agent.so").asFile

    inputs.files(fileTree(perfMapAgentDir) { include("*.c") })
    outputs.file(outputLib)

    commandLine(
        "gcc",
        "-shared", "-fPIC", "-O2", "-std=c11",
        "-o", outputLib.absolutePath,
        "${perfMapAgentDir}/perf_map_agent.c",
        "-I${jdkHome}/include",
        "-I${jdkHome}/include/linux",
        "-lpthread"
    )
}

tasks.named("classes") { dependsOn("compileNative", "compilePerfMapAgent") }

tasks.named<ProcessResources>("processResources") {
    dependsOn("compileNative", "compilePerfMapAgent")
    if (isLinux) {
        from(nativeLibDir)
    }
    filesMatching("jmh.properties") {
        expand(
            mapOf(
                "project" to mapOf("version" to project.version),
                "buildDate" to SimpleDateFormat("yyyy/MM/dd").format(Date())
            )
        )
    }
}
