subprojects {
    apply(plugin = "java-library")

    group = "org.openjdk.jmh"
    version = "1.37.0-codspeed.1"

    repositories {
        mavenCentral()
    }

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
