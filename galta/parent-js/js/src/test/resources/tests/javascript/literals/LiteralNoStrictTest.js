// 0 means octal, unless it cannot be parsed
assertEquals(63, 077)
assertEquals(79, 079)

//
// String escape
//
assertEquals( '\u0001', '\1' );
assertEquals( ' ', '\040' );
assertEquals( ' 0', '\0400' );
assertEquals( ' 0 1', '\0400\401' );
assertEquals( ' 0\u0001', '\0400\001' );
