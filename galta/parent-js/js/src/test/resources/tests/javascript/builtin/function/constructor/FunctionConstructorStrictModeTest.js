// A dynamically-created function is never lexically nested in the calling
// scope - its own strictness must come solely from its own body's directive
// prologue, never from the calling context.
(function () {
	"use strict";
	const h = Function("return typeof this;");
	assertEquals("object", h()); // NOT strict: `this` auto-boxes to globalThis
})();

// Its own "use strict" prologue still makes it genuinely strict.
(function () {
	const h = Function("\"use strict\"; return typeof this;");
	assertEquals("undefined", h()); // strict: no auto-boxing, this stays undefined
})();

// Subclassing Function: super(...) inside a not-yet-`this`-initialized
// derived constructor must not eagerly read `this` (it's still TDZ-poisoned
// at that point) - the generated function is never an arrow function, so
// its parent context's `this` is never actually needed.
class Fn extends Function {}
const fn = new Fn("a", "b", "return a + b;");
assertEquals(5, fn(2, 3));
assertEquals(2, fn.length);
