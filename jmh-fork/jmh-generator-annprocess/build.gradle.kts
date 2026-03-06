dependencies {
    api(project(":jmh-core"))
}

tasks.compileJava {
    options.compilerArgs.add("-proc:none")
}
