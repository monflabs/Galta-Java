const b = "abcd"

assertEquals(false, b.endsWith())
assertEquals(true, b.endsWith(""))
assertEquals(true, b.endsWith("d"))
assertEquals(false, b.endsWith("dd"))
assertEquals(true, b.endsWith("cd"))
assertEquals(true, b.endsWith("abcd"))
assertEquals(false, b.endsWith("aabcd"))
assertEquals(false, b.endsWith("abcda"))

// Second arg is endPosition: limits how much of subject to consider (per spec §22.1.3.7)
// "abcd".endsWith("bc", 3) → "abc".endsWith("bc") → true
assertEquals(true, b.endsWith("bc", 3))
// "abcd".endsWith("cd", 3) → "abc".endsWith("cd") → false
assertEquals(false, b.endsWith("cd", 3))
// "abcd".endsWith("ab", 2) → "ab".endsWith("ab") → true
assertEquals(true, b.endsWith("ab", 2))
// endPosition >= length behaves as no second arg
assertEquals(true, b.endsWith("cd", 4))
assertEquals(true, b.endsWith("cd", 100))
// endPosition = 0: effectively empty subject
assertEquals(false, b.endsWith("a", 0))
assertEquals(true, b.endsWith("", 0))
// undefined endPosition behaves as no second arg
assertEquals(true, b.endsWith("cd", undefined))
// Negative endPosition clamped to 0
assertEquals(false, b.endsWith("a", -1))

// A RegExp search argument must throw TypeError (per spec's IsRegExp check).
assertThrows( () => b.endsWith(/cd/) )
