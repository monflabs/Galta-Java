// `new` can target any PrimaryExpression, not just an identifier/member chain -
// literals, function expressions, class expressions, etc. must all parse (and
// throw TypeError at runtime for non-constructible ones, per the OrdinaryNew
// algorithm - only the object/function/class-expression forms are actually
// constructible here).
assertThrows(TypeError, () => new true);
assertThrows(TypeError, () => new 1);
assertThrows(TypeError, () => new "str");
assertThrows(TypeError, () => new null);

let called = false;
new function() {
	called = true;
	assertEquals(2, arguments.length);
	assertEquals(1, arguments[0]);
	assertEquals(2, arguments[1]);
}(1, 2);
assertEquals(true, called);

class C {
	constructor(v) {
		this.v = v;
	}
}
assertEquals(3, new (class extends C {})(3).v);

// Nested `new`: the outer `new` applies (with no args of its own) to the
// VALUE of the complete inner new-expression - i.e. an already-constructed,
// non-constructible instance, so this throws TypeError.
function Box(v) {
	this.v = v;
}
assertThrows(TypeError, () => new new Box(5));
