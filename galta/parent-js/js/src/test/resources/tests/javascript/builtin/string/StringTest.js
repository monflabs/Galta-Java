// ctor
{
	let s0 = new String()
	assertEquals("",s0)
	
	let s1 = new String("abc")
	assertEquals("abc",s1)
	let s2 = new String(true)
	assertEquals("true",s2)
	let s3 = new String(456)
	assertEquals("456",s3)

	let o1 = new String("a")
	let o2 = new String("a")
	assertNotSame(o1,o2)
}

// function call
{
	let s0 = String()
	assertEquals("",s0)
	let s1 = String("abc")
	assertEquals("abc",s1)
	let s2 = String(true)
	assertEquals("true",s2)
	let s3 = String(456)
	assertEquals("456",s3)

	let o1 = String("a")
	let o2 = String("a")
	assertEqualsStrict(o1,o2)
}

// Properties
{
	let bc = String("a")
	assertThrows( () => bc.prop = 22 );

	let bo = new String("a")
	bo.prop = 22
	assertEquals( 22, bo.prop )
}

// Array Access
{
	const s = "ABC";
	assertEquals("A",s[0]);
	assertEquals("A",s['0']);
	assertEquals("C",s[2]);
	assertEquals("C",s['2']);
	assertEquals(undefined,s[5]);
}


// Check the prototype
assertEquals( String.prototype, Object.getPrototypeOf(""))
