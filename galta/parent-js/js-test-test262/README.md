# JavaScript 262 Tests

The suite is fetched at a pinned commit by a Maven profile, not a git
submodule - see `pom.xml`'s `galtajs.test262.commit` property and
`fetch-externals` profile.

## After cloning the project

Nothing to do - the first `mvn test`/`mvn install` on this module fetches
`test262/` automatically. To fetch ahead of time instead:

```sh
mvn -pl parent-js/js-test-test262 -Pfetch-externals generate-test-resources
```

## Bumping to a newer commit

Edit `galtajs.test262.commit` in `pom.xml`, then re-run the command above
(a plain build won't re-fetch on its own once `test262/` already exists -
`-Pfetch-externals` forces it).
