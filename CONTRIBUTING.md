# Contributing

## Bumping the JMH fork version

The JMH fork uses a versioning scheme like `1.37.0-codspeed.N`. To bump the version across all modules:

```bash
cd jmh-fork && mvn versions:set -DnewVersion=1.37.0-codspeed.2
```

This updates all `pom.xml` files in the multi-module project. After bumping, also update the version reference in `build.gradle.kts`.
