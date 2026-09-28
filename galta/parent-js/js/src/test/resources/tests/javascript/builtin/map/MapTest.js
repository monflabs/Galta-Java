const m = new Map();
assertNotNull(m)
assertEquals(0, m.size)

const mnull = new Map(null);
assertNotNull(mnull)
assertEquals(0, mnull.size)

const mundefined = new Map(undefined);
assertNotNull(mundefined)
assertEquals(0, mundefined.size)

const map3 = new Map([
  [1, "one"],
  [2, "two"],
  [3, "three"],
]);
assertEquals(3, map3.size)
assertEquals("one", map3.get(1))
assertEquals("two", map3.get(2))
assertEquals("three", map3.get(3))

// Check property assignment
{
	const m1 = new Map();
	m1.a = 123
	assertEquals(123, m1.a)
	const sy = Symbol()
	m1[sy] = 456
	assertEquals(456, m1[sy])
	assertEquals(123, m1.a)
}
{
	const HashMap = Java.type("java.util.HashMap") 
	const m1 = new HashMap();
	m1.a = 123
	assertEquals(123, m1.a)
	const sy = Symbol()
	m1[sy] = 456
	assertEquals(456, m1[sy])
	assertEquals(123, m1.a)
}

assertTrue(m instanceof Map);
assertTrue(m instanceof Object);

// getOrInsert: returns the existing value, or inserts and returns the default.
{
	const m1 = new Map();
	assertEquals("v1", m1.getOrInsert("k1", "v1"));
	assertEquals("v1", m1.get("k1"));
	assertEquals("v1", m1.getOrInsert("k1", "v2")); // key already present: default ignored
	assertEquals("v1", m1.get("k1"));

	// -0/+0 are normalized to the same (canonical +0) key.
	const m2 = new Map();
	m2.getOrInsert(-0, 42);
	assertEquals(42, m2.get(0));
	assertEquals(42, m2.get(-0));
}

// getOrInsertComputed: callback receives the (canonical) key, called with this=undefined,
// only invoked when the key is absent, and its result overwrites any mutation
// the callback itself made for that key while running.
{
	const m = new Map();
	let seenThis, seenKey;
	const v = m.getOrInsertComputed("k1", function(key) {
		"use strict";
		seenThis = this;
		seenKey = key;
		return "computed";
	});
	assertEquals("computed", v);
	assertEquals(undefined, seenThis);
	assertEquals("k1", seenKey);
	assertEquals("computed", m.get("k1"));

	let called = false;
	const v2 = m.getOrInsertComputed("k1", () => { called = true; return "other"; });
	assertEquals("computed", v2);
	assertFalse(called);

	// callback mutating the same key: its return value wins over the mutation.
	const m2 = new Map();
	m2.getOrInsertComputed("k", () => { m2.set("k", "mutated"); return "final"; });
	assertEquals("final", m2.get("k"));

	// callback throwing: nothing is inserted.
	const m3 = new Map();
	assertThrows(Error, () => m3.getOrInsertComputed("k", () => { throw new Error(); }));
	assertFalse(m3.has("k"));

	// IsCallable is checked even if the key is already present.
	const m4 = new Map();
	m4.set("k", "existing");
	assertThrows(TypeError, () => m4.getOrInsertComputed("k", 123));
}

// Map.groupBy: throws for a non-callable callback.
{
	assertThrows(TypeError, () => Map.groupBy([], null));
	assertThrows(TypeError, () => Map.groupBy([], undefined));
	assertThrows(TypeError, () => Map.groupBy([], {}));

	const grouped = Map.groupBy([1, 2, 3, 4], (n) => n % 2 === 0 ? "even" : "odd");
	assertEquals([1, 3], grouped.get("odd"));
	assertEquals([2, 4], grouped.get("even"));
}
