// Strict mode
"use strict"

var a = 77

function f() {
  assertEquals(undefined,this)
  return this
}

function g() {
  assertEquals(undefined,this)
  function gg() {
    assertEquals(undefined,this)
    return this
  }
  gg()
  return this
}

assertEquals(77,a)
assertEquals(undefined,f())
assertEquals(77,a)
// A `var`-declared global and the same-named property of globalThis are the
// SAME binding, in both interpreted AND transpiled mode alike (GlobalThis
// bridges a top-level script's own local variable slots to its own
// property lookups).
assertEquals(77,globalThis.a)
assertEquals(undefined,g())
assertEquals(77,a)


const module = {
  getThis: function () {
	// no strict mode, this==globalThis
    return this;
  },
};
const unboundGetThis = module.getThis;
//assertSame(undefined, unboundGetThis())
