// A parenthesized optional-chain member expression used as a call target must
// still preserve `this`, since parens don't strip a Reference.
const a = {
	b() { return this._b; },
	_b: { c: 42 }
};
assertEquals(42, a?.b().c);
assertEquals(42, (a?.b)().c);
assertEquals(42, a.b?.().c);

// Calling a method through super?. must still bind `this` to the current
// `this`, not to the [[HomeObject]]'s prototype used for the lookup.
let called = false;
let context;
class Base {
	method() {
		called = true;
		context = this;
	}
}
class Foo extends Base {
	method() {
		super.method?.();
	}
}
const foo = new Foo();
foo.method();
assertEquals(true, foo === context);
assertEquals(true, called);

// super[computed] must parse and resolve the same as super.name.
class Base2 {
	name() { return 'base2'; }
}
class Foo2 extends Base2 {
	expr() { return super['name']?.(); }
}
assertEquals('base2', new Foo2().expr());

// Once a chain link short-circuits, later links (including any computed
// property expression) must not be evaluated at all.
let touched = 0;
const obj = {
	get a() { return undefined; }
};
obj?.a?.[touched++];
assertEquals(0, touched);

// Reserved words are valid IdentifierNames after a dot.
const arr = [1, 2];
arr.true = 'prop';
assertEquals('prop', arr.true);
assertEquals('prop', arr?.true);

// \u{...} escapes are valid within identifiers and string literals.
const obj2 = { a: 'hello' };
assertEquals('hello', obj2?.\u{0061});
assertEquals('hello', obj2?.a);
assertEquals('a', '\u{0061}');
