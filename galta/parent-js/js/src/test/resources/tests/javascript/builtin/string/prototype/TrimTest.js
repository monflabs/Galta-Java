// Basic trim
assertEquals("ABC", "  ABC  ".trim());

// Already trimmed
assertEquals("ABC", "ABC".trim());

// Only whitespace
assertEquals("", "   ".trim());

// Standard whitespace characters
assertEquals("x", "\t\n\r x \t\n\r".trim());
assertEquals("x", "\tx\t".trim());
assertEquals("x", "\nx\n".trim());

// trimStart removes only leading whitespace
assertEquals("ABC  ", "  ABC  ".trimStart());
assertEquals("ABC", "ABC".trimStart());
assertEquals("", "   ".trimStart());
assertEquals("ABC  ", "\t  ABC  ".trimStart());

// trimEnd removes only trailing whitespace
assertEquals("  ABC", "  ABC  ".trimEnd());
assertEquals("ABC", "ABC".trimEnd());
assertEquals("", "   ".trimEnd());
assertEquals("  ABC", "  ABC\t".trimEnd());

// trimLeft is alias for trimStart, trimRight is alias for trimEnd
assertEquals("ABC  ", "  ABC  ".trimLeft());
assertEquals("  ABC", "  ABC  ".trimRight());

// Empty string
assertEquals("", "".trim());
assertEquals("", "".trimStart());
assertEquals("", "".trimEnd());

// String with only whitespace
assertEquals("", "\t\n\r".trim());
