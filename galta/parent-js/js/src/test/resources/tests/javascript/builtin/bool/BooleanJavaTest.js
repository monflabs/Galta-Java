const bt = true

assertEquals("true",true.$toString())
assertEquals("false",false.$toString())

assertEquals(true,Boolean.$logicalOr(true,false))
