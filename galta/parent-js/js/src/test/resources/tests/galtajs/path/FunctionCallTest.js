function f(a) {
	return a+5;
}

// Simple function call
assertEquals(6, f(1));
assertEquals(7, f?.(2));

// Null function call
const g = null;
assertThrows( () => {g(1)} );
assertEquals(null, g?.(1));

// Arguments
function a0() {
	assertNotNull(arguments);
	assertEquals(0,arguments.length);
}
a0()

function a1(a) {
	assertNotNull(arguments);
	assertEquals(1,arguments.length);
	assertEquals(11,arguments[0]);
	assertEquals(11,a);
}
a1(11)

function a11(a) {
	assertNotNull(arguments);
	assertEquals(0,arguments.length);
	assertEquals(null,arguments[0]);
	assertEquals(null,a);
}
a11()


function argTest() {
	assertNotNull(arguments);
	return arguments.length;
}
assertEquals(0,argTest());
assertEquals(1,argTest(11));
assertEquals(1,argTest(11,));
assertEquals(2,argTest(11,22));
assertEquals(2,argTest(11,22,));

assertParseError("argTest(,)");
assertParseError("argTest(11,,)");
