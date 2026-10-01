# Playground

An interactive scripting playground: a snippet library, editors, a console and engine-specific result views.

- `playground-core`: the engine-agnostic model - snippets and their in-memory files, execution engines, the `ExecutionController` running them, and `SnippetStorage` saving the snippets.
- `playground-ui-swing`: the Swing window (`PlaygroundFrame`).

The GaltaJS playground (`parent-js/js-playground`) plugs the GaltaJS engine in.
