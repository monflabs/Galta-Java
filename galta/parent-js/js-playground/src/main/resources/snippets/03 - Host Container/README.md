# Host Container

APIs a GaltaJS embedder provides on top of the ECMAScript language itself: browser/Node-style timers, a small Node.js-compatible module surface, and Rhino shell compatibility globals for scripts ported from Mozilla Rhino.

## What's available in this playground
- Timers: `setTimeout`, `clearTimeout`, `setInterval`, `clearInterval`, `queueMicrotask`
- Node-style modules: only `fs` is currently implemented, with `readFileSync`/`writeFileSync`
- Rhino shell compatibility: `print`, `version`, `options`, `gc` - see `js-test-rhino`'s own `RhinoShellLibrary`/`RhinoTestEnvironment` for the fuller Rhino ECMA-compliance test harness this playground doesn't otherwise expose
