assertEquals(8,6+2)
assertEquals(8,6+2L)
assertThrows(TypeError, () => 6+2n)
assertEquals(8,6+2.0)
assertEquals(8,6+2.0f)
assertEquals(8,6+2.0)
assertThrows(TypeError, () => 6+2.0m)

assertEquals(8,6+2)
assertEquals(8,6L+2)
assertThrows(TypeError, () => 6n+2)
assertEquals(8,6.0+2)
assertEquals(8,6.0f+2)
assertEquals(8,6.0+2)
assertThrows(TypeError, () => 6.0m+2)

assertEquals(0,0+false)
assertEquals(1,0+true)
assertEquals(2,true+true)

assertEquals("A","A"+"")
assertEquals("A",""+"A")
assertEquals("BA","B"+"A")

assertEquals("A1","A"+1)
assertEquals("Atrue","A"+true)
assertEquals("1A",1+"A")

const o1 = {}
assertEquals("A[object Object]","A"+o1)

const TestClass = Java.type("tests.classes.TestClass")
const j1 = new TestClass()
assertEquals("ATest AAA","A"+j1)

const a1 = [1]
const a2 = [1,2]
assertEquals("A1","A"+a1)
assertEquals("A1,2","A"+a2)
