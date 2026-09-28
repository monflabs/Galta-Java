# Type Hints

GaltaJS accepts TypeScript's type-annotation syntax - on variables, function/method parameters and return types, class fields, generics, `type` aliases, `interface`s, and `declare` ambient declarations - but never checks it. Every annotation is parsed for its syntax and immediately discarded: it produces no AST node and no compiled code, so annotating a value with the wrong type is never an error, and `type`/`interface` declarations never produce a runtime value.

This is opt-in, part of the same "GaltaJS Extensions" bundle as the rest of this category (`JSEnvironment.Builder.enableGaltaJSExtensions()`, or individually via `supportTypeHints(true)`).
