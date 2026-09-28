const b = "456789"

assertEquals("456789",b.slice())
assertEquals("456789",b.slice(0))
assertEquals("",b.slice(null,null))

assertEquals("56789",b.slice(1))
assertEquals("9",b.slice(-1))
assertEquals("89",b.slice(-2))
assertEquals("6789",b.slice(-4))

assertEquals("45678",b.slice(null,-1))


assertEquals("",b.slice(2,2))
assertEquals("",b.slice(3,2))

assertEquals("6",b.slice(2,3))
assertEquals("67",b.slice(-4,-2))


let str1 = 'The morning is upon us.'
assertEquals("he morn",str1.slice(1,8))
assertEquals("morning is upon u",str1.slice(4,-2))
assertEquals("is upon us.",str1.slice(12))
assertEquals("",str1.slice(30))

assertEquals("us.",str1.slice(-3))
assertEquals("us",str1.slice(-3,-1))
assertEquals("The morning is upon us",str1.slice(0,-1))

assertEquals("is u",str1.slice(-11,16))
assertEquals(" is u",str1.slice(11,-7))
assertEquals("n us",str1.slice(-5,-1))
