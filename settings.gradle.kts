rootProject.name = "codspeed-jvm"

include("examples:example-gradle")
includeBuild("jmh-fork") {
    dependencySubstitution {
        substitute(module("org.openjdk.jmh:jmh-core")).using(project(":jmh-core"))
        substitute(module("org.openjdk.jmh:jmh-generator-annprocess")).using(project(":jmh-generator-annprocess"))
        substitute(module("org.openjdk.jmh:jmh-generator-bytecode")).using(project(":jmh-generator-bytecode"))
        substitute(module("org.openjdk.jmh:jmh-generator-reflection")).using(project(":jmh-generator-reflection"))
        substitute(module("org.openjdk.jmh:jmh-generator-asm")).using(project(":jmh-generator-asm"))
    }
}
