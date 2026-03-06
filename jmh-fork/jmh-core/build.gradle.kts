import java.text.SimpleDateFormat
import java.util.Date

dependencies {
    api("net.sf.jopt-simple:jopt-simple:5.0.4")
    api("org.apache.commons:commons-math3:3.6.1")
    implementation("com.google.code.gson:gson:2.11.0")
}

tasks.compileJava {
    options.compilerArgs.addAll(listOf("-proc:none", "-Xlint:serial"))
}

tasks.processResources {
    filesMatching("jmh.properties") {
        expand(
            mapOf(
                "project" to mapOf("version" to project.version),
                "buildDate" to SimpleDateFormat("yyyy/MM/dd").format(Date())
            )
        )
    }
}
