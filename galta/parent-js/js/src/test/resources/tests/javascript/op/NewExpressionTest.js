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

// The target of new is a MemberExpression: computed members, private names and
// async function expressions included
{
    const F = function F(v) { this.v = v; };
    const holder = { list: [F], map: { key: F } };
    const key = "key";
    assertEquals(1, new holder.list[0](1).v);
    assertEquals(2, new holder.map[key](2).v);
    assertEquals(3, new holder["map"].key(3).v);
    assertEquals(undefined, new holder.list[0]().v);
    class K {
        #C = F;
        make(v) { return new this.#C(v); }
    }
    assertEquals(4, new K().make(4).v);
    assertThrows(TypeError, () => new async function () {});
    assertThrows(TypeError, () => new holder.list[1]());
}
