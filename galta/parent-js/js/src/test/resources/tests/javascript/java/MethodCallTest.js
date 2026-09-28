// obj is an object published from a sample library
// we access its fields here

assertNotNull(obj)

obj.setCharVar('V')
assertEquals('V',obj.getCharVar())
assertThrows( () => obj.setCharVar('PI') )
obj.setCharVar('Z'.charAt(0))
assertEquals('Z',obj.getCharVar())

obj.setByteVar(9)
assertEquals(9,obj.getByteVar())

obj.setShortVar(18)
assertEquals(18,obj.getShortVar())

obj.setIntVar(34)
assertEquals(34,obj.getIntVar())

obj.setLongVar(61)
assertEquals(61,obj.getLongVar())
obj.setLongVar(62L)
assertEquals(62L,obj.getLongVar())

obj.setFloatVar(10.63f)
assertEquals(10.63f,obj.getFloatVar())

obj.setDoubleVar(99.76)
assertEquals(99.76,obj.getDoubleVar())

obj.setBigDecimalVar(56.3568m)
assertEquals(56.3568m,obj.getBigDecimalVar())

obj.setStringVar("str 45")
assertEquals("str 45",obj.getStringVar())