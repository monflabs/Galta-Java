// A direct eval's own var/function declarations hoist onto the global object
// as CONFIGURABLE properties (EvalDeclarationInstantiation), unlike ordinary
// top-level var/function declarations, which are non-configurable
// (GlobalDeclarationInstantiation) - see DeleteNoStrictTest.js for the latter.
//
// (BaseProjectTestCase sets the "TRANSPILER" preprocessor symbol to true on a
// genuine interpreted run, and "INTERPRETER" to true on a transpiled run -
// the two symbols are swapped from what their names suggest.) This is
// interpreter-only: the transpiler keeps eval-declared bindings in a fast
// local slot rather than a real, deletable globalThis property.

eval("var evalVar = 1;");
assertEquals( 1, evalVar );
// #if TRANSPILER
assertTrue( Object.getOwnPropertyDescriptor(globalThis, "evalVar").configurable );
assertTrue( delete evalVar );
assertEquals( "undefined", typeof evalVar );
// #endif

eval("function evalFunc() { return 2; }");
assertEquals( 2, evalFunc() );
// #if TRANSPILER
assertTrue( Object.getOwnPropertyDescriptor(globalThis, "evalFunc").configurable );
assertTrue( delete evalFunc );
assertEquals( "undefined", typeof evalFunc );
// #endif

// A genuine top-level var/function stays non-configurable even after eval has
// run in the same program.
var topVar = 3;
function topFunc() { return 4; }
// #if TRANSPILER
assertFalse( Object.getOwnPropertyDescriptor(globalThis, "topVar").configurable );
assertFalse( Object.getOwnPropertyDescriptor(globalThis, "topFunc").configurable );
// #endif
assertFalse( delete topVar );
assertFalse( delete topFunc );
