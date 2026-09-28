// No strict mode

var a = 30;

assertNotNullOrUndefined(globalThis)

function f() {
  assertEquals(globalThis,this)
  this.a = 40
  return this
}

function g() {
  assertEquals(globalThis,this)
  this.a = 50
  function gg() {
    assertEquals(globalThis,this)
    this.a = 52
    return this
  }
  gg()
  return this
}

function h() {
  assertEquals({},this)
  this.a = 60
  function hh() {
    assertEquals(globalThis,this)
    this.a = 62
  }
  return hh()
}

assertEquals(30,a)
assertEquals(globalThis,f())
// A `var`-declared global and the same-named property of globalThis are the
// SAME binding - writing through `this.a=` (this===globalThis here) is
// visible from the bare identifier `a` too, in both interpreted AND
// transpiled mode alike (GlobalThis bridges a top-level script's own
// local variable slots to its own property lookups).
assertEquals(40,a)
assertEquals(40,globalThis.a)
assertEquals(globalThis,g())
assertEquals(52,a)
assertEquals(52,globalThis.a)

const o = new h()
assertEquals(62,a)
assertEquals(62,globalThis.a)


const module = {
  getThis: function () {
	// no strict mode, this==globalThis
    return this;
  },
};
const unboundGetThis = module.getThis;
assertSame(globalThis, unboundGetThis())
