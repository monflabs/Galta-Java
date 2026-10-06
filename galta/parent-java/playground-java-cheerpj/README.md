# Java Playground for CheerpJ

> Not published to Maven Central: it is a browser build of the Java playground, deployed with the documentation site.

The browser build of the [Java playground](../playground-java/README.md): the Swing
application packaged as one jar that [CheerpJ](https://cheerpj.com) runs in the
browser, with no Java installed. The documentation site runs it at
<https://monflabs.github.io/Galta-Java/java-playground/>.

The CheerpJ runtime has neither the JDK's compiler (`jdk.compiler`) nor JShell
(`jdk.jshell`), so this build bundles the Eclipse compiler (`ecj`, EPL 2.0): Galta's
`javacompiler` uses it when the JDK's compiler is missing. A `Main.jshell` snippet is
converted to a `Main` class first (`JShellScriptConverter`), then compiled and run.

ecj reads the JDK classes through the jrt file system of the Java home, which it opens with
`lib/jrt-fs.jar`, and reads the version from the `release` file: CheerpJ's Java home
(`/lt/21`) has neither, while its own `jrt:/` file system has the classes.
`EcjJrtSupport.install()`, called at startup, seeds ecj's caches with that file system and
the runtime's version; it does nothing on a JDK.

`tests.CheerpJSnippetsTest` runs every snippet of the playground the same way: the tests of
this module run with `--limit-modules java.se` (see the pom), a JVM without `jdk.compiler`
and `jdk.jshell`, and with a `java.home` that has no `jrt-fs.jar` nor `release` file.

`cheerpj/diagnostics.html` runs `playground.CheerpJDiagnostics`, which prints to the browser
console what the runtime offers to a compiler (its Java home, the JDK classes as resources,
the `jrt:/` file system, the boot layer's packages).

## Contents

- `src/main/java/playground/JavaPlaygroundCheerpJ.java` - the entry point: the desktop
  playground (`JavaPlayground.configure()`), undecorated and maximized to fill the page.
- `src/main/java/playground/EcjJrtSupport.java` - lets ecj read the JDK classes on CheerpJ.
- `target/javaplayground-cheerpj.jar` - the shaded jar (the playground and all its
  dependencies), built by `mvn package`.
- `cheerpj/index.html` - a page that runs the jar, for local testing;
  `cheerpj/start.sh` serves the folder on port 8080 (`npx http-server`, so it
  needs Node.js).

## Running it locally

```sh
# from the repository root: build the jar (and what it depends on)
mvn -pl galta/parent-java/playground-java-cheerpj -am -DskipTests package
cp galta/parent-java/playground-java-cheerpj/target/javaplayground-cheerpj.jar \
   galta/parent-java/playground-java-cheerpj/cheerpj/
cd galta/parent-java/playground-java-cheerpj/cheerpj && ./start.sh   # http://localhost:8080/
```

CheerpJ needs the page to be served over HTTP (not `file://`), and maps `/app/`
to the root of the web server: `cheerpj/index.html` loads
`/app/javaplayground-cheerpj.jar`, i.e. the jar next to it when `cheerpj/` is the
server root.

## Where it is published

`buildtools/build-site.sh` copies the jar to `docs/java-playground/`, whose
`index.html` computes the `/app/...` path from its own location so it works under
the site's `/Galta-Java/` base path; the publish-docs workflow deploys the site
on every push to master. The same page can be previewed with
`./buildtools/build-site.sh && ./serve-docs.sh` (from the repository root), then
<http://localhost:3030/java-playground/>. Keep the CheerpJ loader version of the
pages in sync with the GaltaJS playground's.
