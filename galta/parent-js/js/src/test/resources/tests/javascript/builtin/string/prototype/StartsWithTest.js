const b = "abcdefabcxyzabc"

assertEquals( false, b.startsWith() )
assertEquals( true, b.startsWith('') )
assertEquals( true, b.startsWith('a') )
assertEquals( true, b.startsWith('abc') )
assertEquals( false, b.startsWith('b') )
assertEquals( true, b.startsWith('b',1) )


let str = 'To be, or not to be, that is the question.'
assertEquals(true, str.startsWith('To be'))         
assertEquals(false, str.startsWith('not to be'))    
assertEquals(true, str.startsWith('not to be', 10))

// A RegExp search argument must throw TypeError (per spec's IsRegExp check).
assertThrows( () => b.startsWith(/abc/) )