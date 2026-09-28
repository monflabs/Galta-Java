const int = Java.type("int")
const long = Java.type("long")
const double = Java.type("double")
const Integer = Java.type("java.lang.Integer")
const Long = Java.type("java.lang.Long")


//
// Integer Overflow
//
{
	let v = Integer.MAX_VALUE 
	
	assertTrue(v instanceof int)
	v--
	assertTrue(v instanceof int)
	assertEquals(Integer.MAX_VALUE-1, v)
	v++
	assertTrue(v instanceof int)
	assertEquals(Integer.MAX_VALUE, v)

	v = v - 1
	assertTrue(v instanceof int)
	assertEquals(Integer.MAX_VALUE-1, v)
	v = v + 1
	assertTrue(v instanceof int)
	assertEquals(Integer.MAX_VALUE, v)
	
	v = v + 1
	assertTrue(v instanceof long)
	assertEquals(Integer.MAX_VALUE+1, v)
	
	v = (Integer.MAX_VALUE-3)/4 // make it divide by 4..
	assertTrue(v instanceof int)
	assertTrue(v*2 instanceof int)
	assertTrue(v*8 instanceof long)
	assertEquals(536870911, v)
	
	v = Integer.MAX_VALUE/3
	assertTrue(v instanceof double)
	assertTrue(v*2 instanceof double)
	assertTrue(v*8 instanceof double)
	assertEquals(7.158278823333334E8, v)

	v = -Integer.MAX_VALUE
	assertTrue(v instanceof int)
}
{
	let v = Integer.MIN_VALUE
	
	assertTrue(v instanceof int)
	v++
	assertTrue(v instanceof int)
	assertEquals(Integer.MIN_VALUE+1, v)
	v--
	assertTrue(v instanceof int)
	assertEquals(Integer.MIN_VALUE, v)

	v = v + 1
	assertTrue(v instanceof int)
	assertEquals(Integer.MIN_VALUE+1, v)
	v = v - 1
	assertTrue(v instanceof int)
	assertEquals(Integer.MIN_VALUE, v)
	
	v = v - 1
	assertTrue(v instanceof long)
	assertEquals(Integer.MIN_VALUE-1, v)
	
	v = Integer.MIN_VALUE/4 
	assertTrue(v instanceof int)
	assertTrue(v*2 instanceof int)
	assertTrue(v*8 instanceof long)
	assertEquals(-2147483648, v*4)
	
	v = Integer.MIN_VALUE/3
	assertTrue(v instanceof double)
	assertTrue(v*2 instanceof double)
	assertTrue(v*4 instanceof double)
	assertEquals(-2.8633115306666665E9, v*4)
	
	v = -Integer.MIN_VALUE
	assertTrue(v instanceof long)
}


//
// Long Overflow
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
	assertTrue(v instanceof double)
	assertEquals(Long.MAX_VALUE+1, v)
	
	v = (Long.MAX_VALUE-1)/2
	assertTrue(v instanceof long)
	assertTrue(v*2 instanceof long)
	assertTrue(v*4 instanceof double)
	assertEquals(4611686018427387903, v)
	
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
	assertTrue(v instanceof double)
	assertEquals(Long.MIN_VALUE-1, v)
	
	v = (Long.MIN_VALUE+2)/3
	assertEquals(-3074457345618258602, v)
	assertTrue(v instanceof long)
	assertTrue(v*2 instanceof long)
	assertTrue(v*4 instanceof double)
	
	v = Long.MIN_VALUE/3
	assertTrue(v instanceof double)
	assertTrue(v*2 instanceof double)
	assertTrue(v*4 instanceof double)
	assertEquals(-1.2297829382473034E19, v*4)

	v = -Long.MIN_VALUE
	assertTrue(v instanceof double)
}