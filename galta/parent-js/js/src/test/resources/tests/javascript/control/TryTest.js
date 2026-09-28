var v = -1

// -------

try { 
	v=1 
	throw new EX('1'); 
	v=2 
} catch(e){
}
assertEquals(1,v)
try { 
	v=1 
	throw new EX('1'); 
	v=2 
} catch(e){
}
assertEquals(1,v)

// -------

try { 
	v=1 
	throw new EX('1'); 
	v=2 
} catch(e){
} finally {
	v=9
}
assertEquals(9,v)
try { 
	v=1 
	throw new EX('1'); 
	v=2 
} catch {
} finally {
	v=9
}
assertEquals(9,v)

function f() {
	try {
		return 5;
	} finally {
		return 6;
	}
}
assertEquals(6,f())

function g() {
	try {
		return 5;
	} finally {
		var a = 6;
	}
}
assertEquals(5,g())

// -------


try { 
	v=4 
	throw new EX('1'); 
	v=5 
} catch(e){
	v=6
}
assertEquals(6,v)
try {
	v=4
	throw new EX('1');
	v=5
} catch {
	v=6
}
assertEquals(6,v)

// -------
// Catch clause destructuring
// (BaseProjectTestCase sets the "TRANSPILER" preprocessor symbol to true on a
// genuine interpreted run, and "INTERPRETER" to true on a transpiled run -
// the two symbols are swapped from what their names suggest.) The transpiler
// doesn't support a destructuring catch parameter (throws a clear error
// rather than silently dropping the bindings), so this is interpreter-only.
// -------

// #if TRANSPILER
try {
	throw [10, 20];
} catch ([a, b]) {
	assertEquals(10, a);
	assertEquals(20, b);
}

try {
	throw {x: 30, y: 40};
} catch ({x, y}) {
	assertEquals(30, x);
	assertEquals(40, y);
}

try {
	throw [1, 2, 3];
} catch ([first, ...rest]) {
	assertEquals(1, first);
	assertEquals([2,3], rest);
}
// #endif

// -------
// Catch/finally completion values (via eval, since a bare script's
// completion value is what surfaces this)
// -------

assertEquals( undefined, eval('1; try { throw null; } catch (err) { }') );
assertEquals( 3, eval('2; try { throw null; } catch (err) { 3; }') );
assertEquals( undefined, eval("for (var i = 0; i < 2; ++i) { if (i) { try {} finally { break; } } 'bad completion'; }") );
