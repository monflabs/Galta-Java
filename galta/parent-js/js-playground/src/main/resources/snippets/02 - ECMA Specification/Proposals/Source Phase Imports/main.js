// A source-phase import binds a module's *Module Source Object* - a handle on
// the module's source - instead of its namespace, and never evaluates the
// module. Only modules that are not JavaScript source text have one: here the
// playground's native (Java-implemented) "fs" module.
import source fsSource from "fs";

console.log(Object.prototype.toString.call(fsSource));   // [object ModuleSource]
console.log(fsSource.source.trim());                     // a native module exposes a placeholder source

// The dynamic form resolves to the very same object.
import.source("fs").then(ms => console.log("same object:", ms === fsSource));

// A JavaScript module has no source phase representation (spec GetModuleSource):
// importing one in the source phase is a SyntaxError.
import.source("./helper.js").then(
	() => console.log("unexpected"),
	e => console.log(`${e.name}: ${e.message}`));

// Its ordinary import is unaffected.
import { greet } from "./helper.js";
console.log(greet("source phase imports"));
