const Integer = Java.type("java.lang.Integer")
const Long = Java.type("java.lang.Long")
const long = Java.type("long")
const double = Java.type("double")


//
// Long Overflow to big int
//
{
	let v = Long.MAX_VALUE 
	
	assertTrue(v instanceof long)
	v--
	assertTrue(v instanceof long)
	assertEquals(Long.MAX_VALUE-1, v)
	v++
	assertTrue(v instanceof long)
	assertEquals(Long.MAX_VALUE, v)

	v = v - 1
	assertTrue(v instanceof long)
	assertEquals(Long.MAX_VALUE-1, v)
	v = v + 1
	assertTrue(v instanceof long)
	assertEquals(Long.MAX_VALUE, v)
	
	v = v + 1
	assertEquals( "bigint", typeof v)
	assertEquals(Long.MAX_VALUE+1, v)
	
	v = (Long.MAX_VALUE-1)/2
	assertTrue(v instanceof long)
	assertTrue(v*2 instanceof long)
	assertEquals( "bigint", typeof (v*4) )
	assertEquals(4611686018427387903n, v)
	
	v = Long.MAX_VALUE/3
	assertTrue(v instanceof double)
	assertTrue(v*2 instanceof double)
	assertTrue(v*4 instanceof double)
	assertEquals(1.2297829382473034E19, v*4)

	v = -Long.MAX_VALUE
	assertTrue(v instanceof long)
}
{
	let v = Long.MIN_VALUE
	
	assertTrue(v instanceof long)
	v++
	assertTrue(v instanceof long)
	assertEquals(Long.MIN_VALUE+1, v)
	v--
	assertTrue(v instanceof long)
	assertEquals(Long.MIN_VALUE, v)

	v = v + 1
	assertTrue(v instanceof long)
	assertEquals(Long.MIN_VALUE+1, v)
	v = v - 1
	assertTrue(v instanceof long)
	assertEquals(Long.MIN_VALUE, v)
	
	v = v - 1
	assertEquals( "bigint", typeof v)
	assertEquals(Long.MIN_VALUE-1, v)
	
	v = (Long.MIN_VALUE+2)/3
	assertEquals(-3074457345618258602, v)
	assertTrue(v instanceof long)
	assertTrue(v*2 instanceof long)
	assertEquals( "bigint", typeof (v*4) )
	
	v = Long.MIN_VALUE/3
	assertTrue(v instanceof double)
	assertTrue(v*2 instanceof double)
	assertTrue(v*4 instanceof double)
	assertEquals(-1.2297829382473034E19, v*4)

	v = -Long.MIN_VALUE
	assertTrue(v instanceof double)
}