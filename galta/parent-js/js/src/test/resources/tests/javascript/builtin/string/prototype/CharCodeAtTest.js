const b = "abcd"

assertEquals(97,b.charCodeAt())
assertEquals(97,b.charCodeAt(0))
assertEquals(98,b.charCodeAt(1))
assertEquals(NaN,b.charCodeAt(-1))
assertEquals(NaN,b.charCodeAt(4))
