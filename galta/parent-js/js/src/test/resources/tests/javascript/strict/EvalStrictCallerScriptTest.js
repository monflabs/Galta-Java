"use strict";

// This whole top-level SCRIPT (not a function) is strict via its own leading
// directive - a direct eval called from here must inherit that strictness at
// parse time, even though none of the eval'd text below has its own
// directive.

assertThrows(function() { eval('with({}) {}'); }, SyntaxError);
assertThrows(function() { eval('var x = 010;'); }, SyntaxError);
assertThrows(function() { eval('var eval;'); }, SyntaxError);
assertThrows(function() { eval('var arguments;'); }, SyntaxError);

// Indirect eval must not inherit this script's strictness. (Deliberately not
// "var eval;" here - a bare `var eval;` via sloppy indirect eval at global
// scope clobbers the real global eval() with undefined, a separate,
// pre-existing GaltaJS var-hoisting bug unrelated to strictness inheritance.)
var indirectEval = eval;
try {
  indirectEval('var arguments;');
} catch (e) {
  fail("indirect eval must not inherit caller strictness: " + e);
}
