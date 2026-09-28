# User's Guide

This guide explains how to embed the GaltaJS engine in a Java application: creating an environment, running expressions, scripts and modules, exchanging values with Java, and using the asynchronous runtime. Every Java or JavaScript snippet in these pages is taken from a JUnit test under `galta/parent-js/js/src/test/java/doc_examples/`, which runs as part of the `js` module build, so the samples are known to work against the current engine.

## Which page do I need?

| I want to... | Page |
|---|---|
| Add the engine to a project and run my first script | [Getting Started](/GaltaJS/UserGuide/GettingStarted) |
| Choose a prebuilt environment or tune builder options | [Environments & Configuration](/GaltaJS/UserGuide/Configuration) |
| Compile once and run many times, share globals, capture output, run files | [Executing Code](/GaltaJS/UserGuide/ExecutingCode) |
| Understand interpreted vs optimized vs transpiled execution, use the Maven plugin | [Execution Modes](/GaltaJS/UserGuide/ExecutionModes) |
| Read JavaScript values from Java, call functions both ways, exchange JSON | [Values & Java API](/GaltaJS/UserGuide/Values) |
| Use Java classes from JavaScript | [Java Interop](/GaltaJS/UserGuide/JavaInterop) |
| Use promises, async/await, timers, or bridge blocking Java work | [Async & Event Loop](/GaltaJS/UserGuide/Async) |
| Load ES modules or CommonJS code from files, memory or Java | [Modules](/GaltaJS/UserGuide/Modules) |
| Handle parse and runtime errors on both sides | [Errors](/GaltaJS/UserGuide/Errors) |
| Pick a regular expression engine | [Regular Expressions](/GaltaJS/UserGuide/RegularExpressions) |
| Keep one source file for several build flavours | [Preprocessor](/GaltaJS/UserGuide/Preprocessor) |
| Set breakpoints, step, or attach Chrome DevTools | [Debugging](/GaltaJS/UserGuide/Debugging) |
| Know what the other `parent-js` Maven artifacts do | [Companion Modules](/GaltaJS/UserGuide/CompanionModules) |

## Related categories

- [Extending the Engine](/GaltaJS/Extending/) — libraries, accessors, native modules.
- [Extensions](/GaltaJS/Extensions/) — the GaltaJS language and runtime extensions beyond ECMAScript.
- [Architecture](/GaltaJS/Architecture/) — how the parser, interpreter, transpiler and async runtime work.
- [Known ECMAScript Gaps](/GaltaJS/KnownGaps) — the list of remaining spec deviations.
