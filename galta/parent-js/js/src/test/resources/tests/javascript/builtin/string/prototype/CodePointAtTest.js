const b = "abcd"

assertEquals(97,b.codePointAt())
assertEquals(97,b.codePointAt(0))
assertEquals(98,b.codePointAt(1))
// Per spec §22.1.3.4: returns undefined for out-of-range position
assertEquals(undefined, b.codePointAt(-1))
assertEquals(undefined, b.codePointAt(4))

assertEquals(65,'ABC'.codePointAt(0))
assertEquals("41",'ABC'.codePointAt(0).toString(16))

assertEquals(128525,'😍'.codePointAt(0) )
assertEquals(128525,'\ud83d\ude0d'.codePointAt(0) )
assertEquals("1f60d",'\ud83d\ude0d'.codePointAt(0).toString(16) )

assertEquals(56845,'😍'.codePointAt(1) )
assertEquals(56845,'\ud83d\ude0d'.codePointAt(1) )
assertEquals("de0d",'\ud83d\ude0d'.codePointAt(1).toString(16) )
