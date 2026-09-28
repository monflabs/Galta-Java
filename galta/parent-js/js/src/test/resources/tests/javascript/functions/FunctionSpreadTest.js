//
// Simple functions
//
const p = [1, 3, 4]

// Function spread parameters
function f1(...v) {
	return v[0]+v[1]+v[2];
}
function f2(a, ...v) {
	return a+v[0]+v[1];
}

assertEquals(6, f1(1,2,3))
assertEquals(6, f1(1,2,3,4))

assertEquals(6, f2(1,2,3))
assertEquals(6, f2(1,2,3,4))


// Mixed with spread calls
function h1(...v) {
	return v[0]+v[1]+v[2];
}
function h2(a, ...v) {
	return a+v[0]+v[1];
}

assertEquals(8, h1(...p))
assertEquals(13, h1(9, ...p))
assertEquals(16, h1(9, 6, ...p))

assertEquals(8, h2(...p))
assertEquals(13, h2(9, ...p))
assertEquals(16, h2(9, 6, ...p))


//
// Object methods
//

const o = {
	f1(...v) {
		return v[0]+v[1]+v[2];
	},
	f2(a, ...v) {
		return a+v[0]+v[1];
	}
}
assertEquals(6, o.f1(1,2,3))
assertEquals(6, o.f1(1,2,3,4))

assertEquals(6, o.f2(1,2,3))
assertEquals(6, o.f2(1,2,3,4))

assertEquals(8, o.f1(...p))
assertEquals(13, o.f1(9, ...p))
assertEquals(16, o.f1(9, 6, ...p))

assertEquals(8, o.f2(...p))
assertEquals(13, o.f2(9, ...p))
assertEquals(16, o.f2(9, 6, ...p))

// Rest parameter as a destructuring pattern (array or object), not just a plain
// identifier, is covered by tests.javascript.functions.RestParameterPatternTest
// (interpreted-mode only -- the transpiler doesn't support this combination).

// A spread argument followed by further plain arguments: the parser must not
// let the "..." token bleed into later, non-spread arguments in the same list.
function sum5(a, b, c, d, e) {
	return [a, b, c, d, e];
}
assertEquals([6, 7, 8, 9, 10], sum5(...[6, 7, 8], 9, 10));
assertEquals([5, 6, 7, 8, 9], sum5(5, ...[6, 7, 8], 9));
assertEquals([5, 6, 7, 8, 9], sum5(5, 6, ...[7, 8], 9));

// Same, for a `new` call's argument list.
function Sum5(a, b, c, d, e) {
	this.values = [a, b, c, d, e];
}
assertEquals([5, 6, 7, 8, 9], new Sum5(5, ...[6, 7, 8], 9).values);
