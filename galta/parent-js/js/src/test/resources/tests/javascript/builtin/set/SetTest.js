const s = new Set();
assertNotNull(s)
assertEquals(0, s.size)

const snull = new Set(null);
assertNotNull(snull)
assertEquals(0, snull.size)

const sundefined = new Set(undefined);
assertNotNull(sundefined)
assertEquals(0, sundefined.size)

const set3 = new Set([1,2,3]);
assertEquals(3, set3.size)
assertTrue(set3.has(1))
assertFalse(set3.has(8))

// Check property assignment
{
	const s1 = new Set();
	s1.a = 123
	assertEquals(123, s1.a)
	const sy = Symbol()
	s1[sy] = 456
	assertEquals(456, s1[sy])
	assertEquals(123, s1.a)
}
{
	const HashSet = Java.type("java.util.HashSet")
	const s1 = new HashSet();
	s1.a = 123
	assertEquals(123, s1.a)
	const sy = Symbol()
	s1[sy] = 456
	assertEquals(456, s1[sy])
	assertEquals(123, s1.a)
}

// If consuming the iterable throws partway through, the constructor must
// close the iterator - call its return() - before propagating, not leave
// it dangling.
{
	let closed = false;
	let errorMessage = null;
	function* gen() { try { yield {}; throw new Error("boom"); } finally { closed = true; } }
	try {
		new Set(gen());
	} catch(e) {
		errorMessage = e.message;
	}
	assertEquals("boom", errorMessage);
	assertEquals(true, closed);
}

// Set.prototype.keys/values/Symbol.iterator are the SAME function object -
// "values" is the canonical definition (its own .name is "values").
{
	assertSame(Set.prototype.keys, Set.prototype.values);
	assertSame(Set.prototype.keys, Set.prototype[Symbol.iterator]);
	assertEquals("values", Set.prototype.values.name);
	assertEquals("values", Set.prototype.keys.name);
}

// union: result starts with `this`'s own elements (in order), then appends
// only the NEW elements from the argument's keys(), in that order.
{
	const s1 = new Set([1, 2]);
	const s2 = new Set([2, 3]);
	assertArrayEquals([1, 2, 3], [...s1.union(s2)]);
}

// difference: branches on relative size - iterates `this` and calls the
// argument's has() when this.size <= arg.size, otherwise iterates the
// argument's keys() and never calls has() at all.
{
	const s1 = new Set([1, 2]);
	const s2 = new Set([2]);
	assertArrayEquals([1], [...s1.difference(s2)]);

	let hasCalled = false;
	const setLike = { size: 1, has: () => { hasCalled = true; return true; }, keys: function*() { yield 2; } };
	assertArrayEquals([1], [...s1.difference(setLike)]);
	assertEquals(false, hasCalled);
}

// intersection
{
	const s1 = new Set([1, 2, 3]);
	const s2 = new Set([2, 3, 4]);
	assertArrayEquals([2, 3], [...s1.intersection(s2)]);
}

// symmetricDifference
{
	const s1 = new Set([1, 2]);
	const s2 = new Set([2, 3]);
	assertArrayEquals([1, 3], [...s1.symmetricDifference(s2)]);
}

// isSubsetOf / isSupersetOf / isDisjointFrom
{
	assertEquals(true, new Set([1, 2]).isSubsetOf(new Set([1, 2, 3])));
	assertEquals(false, new Set([1, 2, 4]).isSubsetOf(new Set([1, 2, 3])));
	assertEquals(true, new Set([1, 2, 3]).isSupersetOf(new Set([1, 2])));
	assertEquals(false, new Set([1, 2]).isSupersetOf(new Set([1, 2, 3])));
	assertEquals(true, new Set([1, 2]).isDisjointFrom(new Set([3, 4])));
	assertEquals(false, new Set([1, 2]).isDisjointFrom(new Set([2, 3])));
}

// -0 in a set-like argument's keys() is normalized to +0 in the result.
{
	const s1 = new Set([1]);
	const setLike = { size: 1, has: () => false, keys: function*() { yield -0; } };
	const result = [...s1.union(setLike)];
	assertEquals(true, Object.is(0, result[1]));
}

// Generic set-like objects work even when keys() returns a plain object
// (not a native generator) satisfying the iterator protocol via next().
{
	const setLike = {
		size: 2,
		has: (v) => v === 1 || v === 2,
		keys: function() {
			let values = [1, 2];
			let i = 0;
			return { next() { return i < values.length ? { value: values[i++], done: false } : { value: undefined, done: true }; } };
		}
	};
	assertArrayEquals([1, 2], [...new Set().union(setLike)]);
}

// GetSetRecord validates "size" via ToNumber, throwing TypeError for NaN
// (including a missing size property or a BigInt size).
{
	const s1 = new Set([1]);
	assertThrows(TypeError, () => s1.union({ has: () => false, keys: function*() {} }));
	assertThrows(TypeError, () => s1.union({ size: NaN, has: () => false, keys: function*() {} }));
	assertThrows(TypeError, () => s1.union({ size: 1n, has: () => false, keys: function*() {} }));
}