const b = "abcabdeabt"

assertEquals(7,b.lastIndexOf("ab"))
assertEquals(0,b.lastIndexOf("abc"))

assertEquals(3,b.lastIndexOf("ab",5))
assertEquals(0,b.lastIndexOf("abc"))

// Not found
assertEquals(-1, b.lastIndexOf("xyz"));

// Position beyond length → searches whole string
assertEquals(7, b.lastIndexOf("ab", 100));

// Negative position → -1 (like position 0)
assertEquals(-1, "abc".lastIndexOf("a", -1));

// Position exactly 0
assertEquals(0, "abc".lastIndexOf("a", 0));

// Empty string found at every position
assertEquals(3, "abc".lastIndexOf("", 3));
assertEquals(2, "abc".lastIndexOf("", 2));

// Case sensitive
assertEquals(-1, "Hello".lastIndexOf("hello"));

// Coerces searchString to string
assertEquals(3, "a1b1".lastIndexOf(1));

// NaN position → searches whole string (NaN converts to 0 but spec says Infinity)
// per spec, ToNumber(NaN)=NaN and the position is treated as +Infinity → searches all
assertEquals(7, b.lastIndexOf("ab", NaN));
