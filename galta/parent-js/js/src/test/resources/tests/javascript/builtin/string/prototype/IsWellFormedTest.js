// Well-formed strings
assertTrue("hello".isWellFormed());
assertTrue("😀".isWellFormed()); // U+1F600 (valid surrogate pair)
assertTrue("a😀b".isWellFormed()); // mixed content

// Ill-formed strings (lone surrogates or bad pairs)
assertFalse("\uD800".isWellFormed()); // lone high surrogate
assertFalse("\uDC00".isWellFormed()); // lone low surrogate
assertFalse("x\uD800y".isWellFormed()); // high surrogate in the middle
assertFalse("\uD800\uD800".isWellFormed()); // two high surrogates
assertFalse("\uDC00\uD800".isWellFormed()); // reversed surrogate pair

// Edge cases
assertTrue("".isWellFormed()); // empty string
assertTrue("ok\u0000null".isWellFormed()); // includes null character
