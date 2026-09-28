// obj is an object published from a sample library
// we access its fields here

assertNotNull(obj)

obj.charVar='N'
assertEquals('N',obj.charVar)
assertThrows( () => obj.charVar='N2' )

obj.byteVar=32
assertEquals(32,obj.byteVar)

obj.shortVar=1562
assertEquals(1562,obj.shortVar)

obj.intVar=-562
assertEquals(-562,obj.intVar)

obj.longVar=47892L
assertEquals(47892L,obj.longVar)

obj.floatVar=12.562f
assertEquals(12.562f,obj.floatVar)

obj.doubleVar=562.892
assertEquals(562.892,obj.doubleVar)

obj.bigDecimalVar=20m
assertEquals(20m,obj.bigDecimalVar)

obj.booleanVar=false
assertEquals(false,obj.booleanVar)

obj.stringVar='A new String'
assertEquals('A new String',obj.stringVar)


// Bean
obj.intProp=-562
assertEquals(-562,obj.intProp)
