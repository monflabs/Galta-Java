const b = "abcd"

assertEquals(0,b.indexOf("a"))
assertEquals(2,b.indexOf("c"))
assertEquals(3,b.indexOf("d"))
assertEquals(-1,b.indexOf("z"))

assertEquals(0,b.indexOf("a",0))
assertEquals(2,b.indexOf("c",0))
assertEquals(3,b.indexOf("d",0))
assertEquals(-1,b.indexOf("z",0))

assertEquals(-1,b.indexOf("a",1))
assertEquals(2,b.indexOf("c",1))
assertEquals(3,b.indexOf("d",1))

assertEquals(-1,b.indexOf("a",3))
assertEquals(-1,b.indexOf("c",3))
assertEquals(3,b.indexOf("d",3))

assertEquals(-1,b.indexOf("d",8))

// Empty string is found at any position
assertEquals(0, "abc".indexOf(""));
assertEquals(2, "abc".indexOf("", 2));
assertEquals(3, "abc".indexOf("", 3));
// Clamped to length when position > length
assertEquals(3, "abc".indexOf("", 10));

// Negative position treated as 0
assertEquals(0, "abc".indexOf("a", -5));

// Case sensitive
assertEquals(-1, "Hello".indexOf("hello"));

// Coerces searchString to string
assertEquals(1, "a1b".indexOf(1));

// null searchString coerces to string "null"
assertEquals(0, "null is here".indexOf(null));
