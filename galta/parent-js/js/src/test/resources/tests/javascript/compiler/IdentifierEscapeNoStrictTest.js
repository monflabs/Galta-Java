// An escape in an IdentifierName must denote a character allowed there:
// ID_Start, "$" or "_" first; ID_Continue, "$", ZWNJ or ZWJ after.
var \u0061b = 1;
assertEquals(1, ab);
var \u{62}\u{63} = 2;
assertEquals(2, bc);
var a\u200Cb = 3;
assertEquals(3, a\u200Cb);
var \u{1d49c} = 4;
assertEquals(4, \u{1d49c});
var $\u0031 = 5;
assertEquals(5, $1);
var o = { v\u0061r: 6 };
assertEquals(6, o.var);
assertEquals(6, o.v\u0061r);

assertThrows(function() { eval('var x\\u000Ay'); }, SyntaxError);
assertThrows(function() { eval('var a\\u0020b'); }, SyntaxError);
assertThrows(function() { eval('var \\u0031a'); }, SyntaxError);
assertThrows(function() { eval('var \\u{110000}'); }, SyntaxError);
assertThrows(function() { eval('var \\uD835\\uDC9C'); }, SyntaxError);
assertThrows(function() { eval('var \\u2E2F'); }, SyntaxError);
assertThrows(function() { eval('x\\u0000'); }, SyntaxError);

// A reserved word cannot be spelled with escapes where an identifier is expected
assertThrows(function() { eval('var v\\u0061r = 1'); }, SyntaxError);
assertThrows(function() { eval('var \\u0074his'); }, SyntaxError);
assertThrows(function() { eval('async function f() { var \\u0061wait; }'); }, SyntaxError);
assertThrows(function() { eval('function* g() { var yi\\u0065ld; }'); }, SyntaxError);
