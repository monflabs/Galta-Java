// Annex B.3.3: a sloppy-mode block-scoped function declaration is hoisted
// to the enclosing function/global scope as a var-like binding - but this
// hoist must be suppressed entirely in strict mode (the declaration stays
// block-scoped only).

// Sloppy mode: hoist still works (regression guard for HoistingTest.js's
// existing coverage, exercised again here alongside the switch-case form).
(function() {
	if (true) {
		function f() { return "sloppy"; }
	}
	assertEquals("sloppy", f());

	switch (1) {
		case 1:
			function swSloppy() { return "swSloppy"; }
	}
	assertEquals("swSloppy", swSloppy());
})();

// Strict mode: hoist suppressed - the outer binding must not exist at all.
(function() {
	"use strict";

	assertThrows(ReferenceError, () => g);
	{
		function g() { return "strict"; }
	}
	assertThrows(ReferenceError, () => g);

	assertThrows(ReferenceError, () => sw);
	switch (1) {
		case 1:
			function sw() { return "sw"; }
	}
	assertThrows(ReferenceError, () => sw);
})();

// Strict mode: the function is still perfectly usable from INSIDE its own
// block/case - only the outer hoist is suppressed.
(function() {
	"use strict";
	{
		function h() { return "inside"; }
		assertEquals("inside", h());
	}
	switch (1) {
		case 1:
			function swInside() { return "swInside"; }
			assertEquals("swInside", swInside());
	}
})();

// A non-block-nested (ordinary top-level) strict-mode function declaration
// is unaffected either way.
(function() {
	"use strict";
	function ordinary() { return "ordinary"; }
	assertEquals("ordinary", ordinary());
})();
