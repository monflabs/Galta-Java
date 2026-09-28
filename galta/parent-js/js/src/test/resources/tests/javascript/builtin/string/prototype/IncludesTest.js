const b = "abcd"

assertEquals(true,b.includes("a"))
assertEquals(true,b.includes("c"))
assertEquals(true,b.includes("d"))
assertEquals(false,b.includes("z"))

assertEquals(true,b.includes("a",0))
assertEquals(true,b.includes("c",0))
assertEquals(true,b.includes("d",0))
assertEquals(false,b.includes("z",0))

assertEquals(false,b.includes("a",1))
assertEquals(true,b.includes("c",1))
assertEquals(true,b.includes("d",1))

assertEquals(false,b.includes("a",3))
assertEquals(false,b.includes("c",3))
assertEquals(true,b.includes("d",3))

assertEquals(false,b.includes("d",8))

// A RegExp search argument must throw TypeError (per spec's IsRegExp check).
assertThrows( () => b.includes(/bc/) )
