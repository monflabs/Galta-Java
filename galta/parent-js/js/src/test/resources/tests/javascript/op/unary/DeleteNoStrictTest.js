var v1 = 9;
const v2 = 11;
let v3 = 12;
function f1() {return 79;}

assertEquals( 9, v1 );
assertFalse( delete v1 );
assertEquals( 9, v1 );

// This fails with transpiler in non strict mode as the VAR type is not available
assertFalse( delete v2 );
assertEquals( 11, v2 );
assertFalse( delete v3 );
assertEquals( 12, v3 );

// A function declaration creates a non-configurable global binding too,
// same as `var` above.
assertFalse( delete f1 );
assertEquals( 79, f1() );

assertTrue( delete v4 );

// A non-configurable global (built-in), unlike an ordinary var, cannot be deleted.
assertFalse( delete NaN );
assertEquals( NaN, NaN );

// delete of a non-Reference (e.g. a call result) still evaluates its operand
// (for side effects) and trivially succeeds.
let called = false;
function f2() { called = true; return 1; }
assertTrue( delete f2() );
assertTrue( called );
