const m = new WeakMap();
assertNotNull(m)

const mnull = new WeakMap(null);
assertNotNull(mnull)
assertFalse(mnull.has(null))

const mundefined = new WeakMap(undefined);
assertNotNull(mundefined)
assertFalse(mnull.has(mundefined))

const ONE = {}
const TWO = new String("two")
const THREE = Symbol("sym1")
const FOUR = new Number(79)

const mapp = new WeakMap([
  [ONE, "one"],
  [TWO, "two"],
  [THREE, "three"],
]);
assertEquals("one", mapp.get(ONE))
assertEquals("two", mapp.get(TWO))
assertEquals("three", mapp.get(THREE))

const map3 = new WeakMap()
map3.set(ONE,"one")
map3.set(TWO,"two")
map3.set(THREE,"three")
map3.set(FOUR,"four")
map3.set(Symbol.iterator,"five")
assertEquals("one", map3.get(ONE))
assertEquals("two", map3.get(TWO))
assertEquals("three", map3.get(THREE))
assertEquals("four", map3.get(FOUR))
assertEquals("five", map3.get(Symbol.iterator))

assertThrows( () => map3.set(null,"whatever") )
assertThrows( () => map3.set(undefined,"whatever") )
assertThrows( () => map3.set(1,"whatever") )
assertThrows( () => map3.set("abc","whatever") )
assertThrows( () => map3.set(true,"whatever") )
assertThrows( () => map3.set(Symbol.for("blob"),"whatever") )


// Check property assignment
{
	const m1 = new WeakMap();
	m1.a = 123
	assertEquals(123, m1.a)
	const sy = Symbol()
	m1[sy] = 456
	assertEquals(456, m1[sy])
	assertEquals(123, m1.a)
}

// getOrInsertComputed: callback receives (key) as its sole argument, called with this=undefined
{
	const m = new WeakMap();
	const key = {};
	let seenThis, seenArgs;
	const v = m.getOrInsertComputed(key, function() {
		"use strict";
		seenThis = this;
		seenArgs = arguments;
		return "computed";
	});
	assertEquals("computed", v);
	assertEquals(undefined, seenThis);
	assertEquals(1, seenArgs.length);
	assertEquals(key, seenArgs[0]);
	assertEquals("computed", m.get(key));

	// Existing key: callback must not be called again
	let called = false;
	const v2 = m.getOrInsertComputed(key, () => { called = true; return "other"; });
	assertEquals("computed", v2);
	assertFalse(called);
}
