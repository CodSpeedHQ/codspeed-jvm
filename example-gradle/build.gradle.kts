plugins {
    java
    id("me.champeau.jmh") version "0.7.2"
}

group = "io.codspeed"
version = "1.0-SNAPSHOT"

jmh {
    jmhVersion.set("1.37.0-codspeed.1")
}
