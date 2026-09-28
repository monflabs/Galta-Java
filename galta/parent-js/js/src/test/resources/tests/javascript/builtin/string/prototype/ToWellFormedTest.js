// Valid strings should be returned unchanged
assertEquals("hello", "hello".toWellFormed());
assertEquals("😀", "😀".toWellFormed()); // U+1F600
assertEquals("a😀b", "a\uD83D\uDE00b".toWellFormed());

// Lone surrogates should be replaced with U+FFFD
assertEquals("\uFFFD", "\uD800".toWellFormed()); // lone high surrogate
assertEquals("\uFFFD", "\uDC00".toWellFormed()); // lone low surrogate
assertEquals("x\uFFFDy", "x\uD800y".toWellFormed()); // high surrogate alone
assertEquals("\uFFFD\uFFFD", "\uD800\uD800".toWellFormed()); // two high surrogates

// Edge cases
assertEquals("", "".toWellFormed()); // empty string
assertEquals("\uFFFD\uFFFD", "\uDC00\uD800".toWellFormed()); // reversed surrogate pair
assertEquals("ok\u0000null", "ok\u0000null".toWellFormed()); // includes null char
