// Assigning to a const binding is not an early error: the code parses, and
// the assignment throws a TypeError when it runs (ECMAScript
// SetMutableBinding on an immutable binding). Each case runs in a fresh
// function so that the const is local to it.
function assertConstAssignThrows(code) {
	assertParse(code);
	assertThrows(TypeError, () => Function(code)());
}

assertConstAssignThrows("const v = 14; v=1");
assertConstAssignThrows("const v = 14; v+=1");
assertConstAssignThrows("const v = 14; v-=1");
assertConstAssignThrows("const v = 14; v*=1");
assertConstAssignThrows("const v = 14; v/=1");
assertConstAssignThrows("const v = 14; v%=1");

assertConstAssignThrows("const v = 14; v&=1");
assertConstAssignThrows("const v = 14; v|=1");
assertConstAssignThrows("const v = 14; v^=1");
assertConstAssignThrows("const v = 14; v&&=1");
// Logical assignments only assign when they short-circuit the other way
assertConstAssignThrows("const v = 0; v||=1");
assertConstAssignThrows("const v = null; v??=1");
Function("const v = 14; v||=1; v??=1")();

assertConstAssignThrows("const v = 14; v++");
assertConstAssignThrows("const v = 14; v--");
assertConstAssignThrows("const v = 14; ++v");
assertConstAssignThrows("const v = 14; --v");

// The const is still readable, and the failed assignment leaves it unchanged
assertEquals(14, Function("const v = 14; try { v = 1 } catch(e) {} return v")());

// Strict mode: assigning to an undeclared variable parses too, and throws a
// ReferenceError when it runs
function assertUndeclaredAssignThrows(code) {
	assertParse(code);
	assertThrows(ReferenceError, () => Function(code)());
}

assertUndeclaredAssignThrows("'use strict'; undeclaredV=1");
assertUndeclaredAssignThrows("'use strict'; undeclaredV+=1");
assertUndeclaredAssignThrows("'use strict'; undeclaredV-=1");
assertUndeclaredAssignThrows("'use strict'; undeclaredV*=1");
assertUndeclaredAssignThrows("'use strict'; undeclaredV/=1");
assertUndeclaredAssignThrows("'use strict'; undeclaredV%=1");

assertUndeclaredAssignThrows("'use strict'; undeclaredV&=1");
assertUndeclaredAssignThrows("'use strict'; undeclaredV|=1");
assertUndeclaredAssignThrows("'use strict'; undeclaredV^=1");
assertUndeclaredAssignThrows("'use strict'; undeclaredV&&=1");
assertUndeclaredAssignThrows("'use strict'; undeclaredV||=1");
assertUndeclaredAssignThrows("'use strict'; undeclaredV??=1");

assertUndeclaredAssignThrows("'use strict'; undeclaredV++");
assertUndeclaredAssignThrows("'use strict'; undeclaredV--");
assertUndeclaredAssignThrows("'use strict'; ++undeclaredV");
assertUndeclaredAssignThrows("'use strict'; --undeclaredV");
assertFalse("undeclaredV" in globalThis);

// Without quotes, "use strict" is not a directive but two identifiers in a
// row: a syntax error
assertParseError("use strict; v=1");
