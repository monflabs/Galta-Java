const map3 = new Map([
  [1, "one"],
  [2, "two"],
  [3, "three"],
]);
assertEquals(3, map3.size)

// delete() returns true when key was present
assertTrue(map3.delete(2));
assertEquals(2, map3.size)
assertEquals("one", map3.get(1))
assertEquals(undefined, map3.get(2))
assertEquals("three", map3.get(3))

// delete() returns false when key was not present
assertFalse(map3.delete(99));
assertFalse(map3.delete(2)); // already deleted

// Delete with various key types
const m = new Map([[null, 'a'], [undefined, 'b'], [NaN, 'c']]);
assertTrue(m.delete(null));
assertTrue(m.delete(undefined));
assertTrue(m.delete(NaN));
assertEquals(0, m.size);

// Delete non-existent key doesn't throw
assertFalse(new Map().delete('nonexistent'));
