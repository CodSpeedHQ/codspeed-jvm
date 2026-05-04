subprojects {
    if (name.startsWith("native-")) {
        return@subprojects
    }

    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    group = "io.codspeed.jmh"
    version = "0.1.1"

    repositories {
        mavenCentral()
    }

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
            }
        }
    }
}
