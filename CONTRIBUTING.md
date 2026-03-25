# Contributing

## Bumping the JMH fork version

The JMH fork is published under group ID `io.codspeed.jmh`. To bump the version across all modules:

```bash
cd jmh-fork && mvn versions:set -DnewVersion=x.y.z
```

This updates all `pom.xml` files in the multi-module project. After bumping, also update the version reference in `build.gradle.kts`.
