# External test suites

`js-test-test262` and `js-test-rhino` each depend on an external test suite
(TC39 test262, Mozilla's Rhino ECMA suite) that is not a git submodule -
it's fetched at a pinned commit by a Maven profile, so bumping the suite is
a one-line property edit instead of a submodule-update-and-commit dance.
See each module's own `pom.xml`/`CLAUDE.md` for its exact pinned commit.

## After a clean clone

Nothing to do - the first `mvn test`/`mvn install` that touches either
module fetches its suite automatically (the `fetch-externals` profile
auto-activates whenever the suite's checkout is missing). Once fetched,
later builds skip the fetch and stay offline.

To fetch ahead of time instead of waiting for the first test run:

```sh
mvn -pl parent-js/js-test-test262 -Pfetch-externals generate-test-resources
mvn -pl parent-js/js-test-rhino -Pfetch-externals generate-test-resources
```

## Bump a suite to a newer commit

Edit `galtajs.test262.commit` (in `js-test-test262/pom.xml`) or
`galtajs.rhino.commit` (in `js-test-rhino/pom.xml`) to the new commit SHA,
then explicitly re-run the fetch command above for that module - the
checkout already exists from the old commit, so auto-activation won't
trigger on its own; `-Pfetch-externals` forces it regardless.
