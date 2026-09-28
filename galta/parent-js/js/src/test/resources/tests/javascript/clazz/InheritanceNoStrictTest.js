// Intentionally no "use strict" anywhere, and this runs under an environment where
// env.isStrictMode() is false too: class bodies must still behave as strict-mode code
// on their own.

class Base {
	constructor(a, b, c) {
		this.base = [a, b, c];
	}
}
class Child extends Base {
	constructor(b) {
		super(1, 2, 3);
		this.child = b;
	}
}

const c = new Child(5);
assertEquals(5, c.child);
assertEquals(1, c.base[0]);
assertEquals(2, c.base[1]);
assertEquals(3, c.base[2]);


// Same, but with a rest parameter constructor on both sides.
class RestBase {
	constructor(...a) {
		this.base = a;
	}
}
class RestChild extends RestBase {
	constructor(...b) {
		super(1, 2, 3);
		this.child = b;
	}
}

const rc = new RestChild(4, 5);
assertEquals(2, rc.child.length);
assertEquals(4, rc.child[0]);
assertEquals(5, rc.child[1]);
assertEquals(3, rc.base.length);
assertEquals(1, rc.base[0]);
assertEquals(2, rc.base[1]);
assertEquals(3, rc.base[2]);


// A class's own methods run in strict mode even though the file isn't strict:
// `this` in a plain (non-method) call is undefined in strict mode, but classes
// don't expose that directly -- verify indirectly via a detached method call.
class StrictCheck {
	isThisUndefined() {
		return this === undefined;
	}
}
const detached = new StrictCheck().isThisUndefined;
assertTrue(detached());
