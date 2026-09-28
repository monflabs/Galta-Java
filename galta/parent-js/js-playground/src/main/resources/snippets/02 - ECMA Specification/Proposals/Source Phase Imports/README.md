# Source Phase Imports

[https://github.com/tc39/proposal-source-phase-imports](https://github.com/tc39/proposal-source-phase-imports)

`import source x from "m"` and `import.source("m")` bind the module's *Module Source Object* (prototype chain ending in `%AbstractModuleSource%.prototype`, a `source` getter with the module's text) without loading, linking or evaluating the module.

Per the proposal a JavaScript module has no source-phase representation, so importing one this way is a `SyntaxError`. In GaltaJS the module kinds that do have one are native (Java-implemented) modules - a `// native module: <name>` placeholder - and pre-transpiled classes that retain their source.
