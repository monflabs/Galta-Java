# GaltaJS Playground for CheerpJ

The browser build of the GaltaJS playground: the Swing playground application
(`js-playground`) packaged as one jar that [CheerpJ](https://cheerpj.com) runs
in the browser, with no Java installed.

- `src/main/java/playground/GaltaJSPlaygroundCheerpJ.java` - the entry point,
  the desktop playground set up for the browser.
- `target/jsplayground-cheerpj.jar` - the shaded jar (the playground and all its
  dependencies), built by `mvn package`.
- `cheerpj/index.html` - a page that runs the jar, for local testing.

The module is not published to Maven Central.

## Running it locally

```sh
# from the repository root: build the jar (and what it depends on)
mvn -pl galta/parent-js/js-playground-cheerpj -am -DskipTests package
cp galta/parent-js/js-playground-cheerpj/target/jsplayground-cheerpj.jar \
   galta/parent-js/js-playground-cheerpj/cheerpj/
cd galta/parent-js/js-playground-cheerpj/cheerpj && ./start.sh   # http://localhost:8080/
```

CheerpJ needs the page to be served over HTTP (not `file://`), and maps `/app/`
to the root of the web server: `cheerpj/index.html` loads
`/app/jsplayground-cheerpj.jar`, i.e. the jar next to it when `cheerpj/` is the
server root.

## Where it is published

The documentation site runs it at
<https://monflabs.github.io/Galta-Java/playground/>:
`buildtools/build-site.sh` copies the jar to `docs/playground/`, whose
`index.html` computes the `/app/...` path from its own location so it works under
the site's `/Galta-Java/` base path. The same page can be previewed with
`./buildtools/build-site.sh && ./serve-docs.sh`, then
<http://localhost:3030/playground/>. Keep the CheerpJ loader version of the two
pages in sync.
