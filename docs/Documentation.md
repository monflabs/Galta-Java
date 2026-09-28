# Generating the Documentation

The documentation is a [docsify](https://docsify.js.org) site: plain Markdown
rendered in the browser, with no build step and nothing generated into the
repository. Editing a `.md` file under `docs/` is the whole workflow.

## Viewing it locally

From the repository root:

```sh
npx docsify-cli serve --port 3030 docs
```

then open [http://localhost:3030](http://localhost:3030). The server watches
the files, so a saved edit shows up on reload.

With the docsify CLI installed globally, `docsify serve --port 3030 docs`
does the same thing.

## How the site is wired

| File | Role |
|---|---|
| `docs/index.html` | The whole docsify configuration - theme, search, and the `alias` that maps every folder's `_sidebar.md` back to the single root one |
| `docs/_sidebar.md` | The one navigation tree for the entire site |
| `docs/README.md` | The home page, served at `/` |
| `docs/<Section>/README.md` | A section's landing page, served at `/<Section>/` |

Links between pages are site-absolute and carry no `.md` extension -
`/GaltaJS/UserGuide/Modules`, not `Modules.md` - so they resolve the same way
from any page.

## Adding a page

1. Create the `.md` file in the right folder under `docs/`.
2. Add one line to `docs/_sidebar.md`; nothing discovers pages automatically.
3. If the page documents an API, back its snippets with a test - see below.

## Keeping the samples honest

Every Java and JavaScript sample in the documentation is backed by a JUnit
test in a `doc_examples` package of the module it documents:

| Section | Samples | Run by |
|---|---|---|
| GaltaJS | `galta/parent-js/js/src/test/java/doc_examples` | `AllDocExamplesTests` |
| GaltaJSON core | `galta/parent-json/json-tests/src/test/java/doc_examples/json` | `AllJsonDocExamplesTests` |
| GaltaJSON add-on modules | `src/test/java/doc_examples/<module>` in each module | the module's `All*Tests` suite |
| Utilities | `galta/parent-utilities/utilities-tests/src/test/java/doc_examples/util` | `AllUtilDocExamplesTests` |
| File systems, Java compiler, test support | `src/test/java/doc_examples/<module>` in each module | the module's `All*Tests` suite |

Each suite runs on every build of its module. A sample that stops
compiling or stops producing the documented result fails the build rather
than rotting quietly on the page, so a documented behaviour change is caught
where it happens.

When adding or changing a sample, put the real assertion in the matching
`doc_examples` class and reference it from the page (`Sample:
doc_examples/ModulesExamples.java`), so a reader can find the executable
version of what they are looking at. A new `Examples` class must be added to
its module's suite, or it never runs.
