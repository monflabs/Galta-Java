function isStrictMode() {
  return this === undefined;
}

function f() {
	"use strict"
	function isStrictMode2() {
	  return this === undefined;
	}
	assertFalse(isStrictMode())
	assertTrue(isStrictMode2())
}

assertFalse(isStrictMode())
f()
