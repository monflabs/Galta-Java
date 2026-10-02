# GaltaJS All-in-One

> Not published to Maven Central: it is a build convenience; Maven users depend on [`js`](../js/README.md), which brings the same classes transitively.

A single "fat" jar holding the GaltaJS engine ([`js`](../js/README.md)) and all
its runtime dependencies (`json`, `javacompiler`, `utilities`), for deployments
that want one jar on the classpath. The module has no sources: the
`maven-shade-plugin` merges the jars, strips signature files and a few packages
the engine does not need at run time (`org/eclipse/**`, Commons Logging), and
keeps a single copy of the Galta `LICENSE` and `NOTICE`.

## Getting the jar

Every [GitHub release](https://github.com/monflabs/Galta-Java/releases) attaches
it as `galtajs-all-<version>.jar`. To build it from source (from `galta/`):

```sh
mvn -pl parent-js/js-all -am -DskipTests package   # writes parent-js/js-all/target/galtajs-all.jar
```

The jar has no `Main-Class`: put it on the classpath of the application that
embeds the engine.

## Documentation

- [Getting Started](../../../docs/GaltaJS/UserGuide/GettingStarted.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/UserGuide/GettingStarted))
- [Companion Modules](../../../docs/GaltaJS/UserGuide/CompanionModules.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/UserGuide/CompanionModules))
- [API reference](https://monflabs.github.io/Galta-Java/#/API) (the `js` module)
