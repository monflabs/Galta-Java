var calls = 0;
var done = false;

// Basic dynamic import: resolves to a Promise, settled with the module's
// namespace (default export currently included the same way the static
// `import * as ns` codepath already exposes it - see ASTImportCall).
var p1 = import("a.js").then(function(ns) {
	calls++;
	assertEquals("aa", ns.default);
}, function(reason) {
	fail("import(\"a.js\") unexpectedly rejected: " + reason);
});

// A named export, and a relative specifier resolved against the SAME
// resolver root as static imports.
var p2 = import("mod/b.js").then(function(ns) {
	calls++;
	assertEquals("bb-mod", ns.b);
});

// A non-existent module must REJECT (not throw synchronously) - the
// specifier is only resolved inside the deferred microtask.
var p3 = import("does-not-exist.js").then(function() {
	fail("import(\"does-not-exist.js\") unexpectedly resolved");
}, function(reason) {
	calls++;
	assertTrue(reason instanceof TypeError);
});

// import() with a computed (non-literal) specifier expression - proves
// this is a genuine expression, not special-cased string-literal syntax.
var name = "a.js";
var p4 = import(name).then(function(ns) {
	calls++;
	assertEquals("aa", ns.default);
});

assertEquals(0, calls); // nothing has run synchronously yet - all 4 are still pending microtasks

Promise.all([p1, p2, p3, p4]).then(function() {
	done = true;
	assertEquals(4, calls);
}, function(reason) {
	fail("unexpected rejection: " + reason);
});
