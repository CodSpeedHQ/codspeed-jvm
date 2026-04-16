import java.text.SimpleDateFormat
import java.util.Date

val isLinux = org.gradle.internal.os.OperatingSystem.current().isLinux

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
    destinationDirectory = layout.buildDirectory.dir("classes/jni")
    options.headerOutputDirectory = layout.buildDirectory.dir("generated/jni-headers")
    onlyIf { isLinux }
}

tasks.named<ProcessResources>("processResources") {
    if (isLinux) {
        val instrumentHooksLink = project(":jmh-core:native-instrument-hooks")
            .tasks.named<org.gradle.nativeplatform.tasks.LinkSharedLibrary>("linkRelease")
        val perfMapLink = project(":jmh-core:native-perf-map-agent")
            .tasks.named<org.gradle.nativeplatform.tasks.LinkSharedLibrary>("linkRelease")
        from(instrumentHooksLink.flatMap { it.linkedFile })
        from(perfMapLink.flatMap { it.linkedFile })
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
