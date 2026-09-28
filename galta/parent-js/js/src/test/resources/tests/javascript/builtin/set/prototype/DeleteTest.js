const set3 = new Set([1,2,3]);
assertEquals(3, set3.size)

// delete() returns true when value was present
assertTrue(set3.delete(2));
assertEquals(2, set3.size)
assertTrue(set3.has(1))
assertFalse(set3.has(2))
assertTrue(set3.has(3))

// delete() returns false when value was not present
assertFalse(set3.delete(99));
assertFalse(set3.delete(2)); // already deleted

// Delete with special values
const s = new Set([null, undefined, NaN]);
assertTrue(s.delete(null));
assertTrue(s.delete(undefined));
assertTrue(s.delete(NaN));
assertEquals(0, s.size);

// Delete non-existent value from empty set
assertFalse(new Set().delete('nonexistent'));
