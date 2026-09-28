// ctor
{
	let b = new Boolean()
	assertEquals(false,b)
	let bf = new Boolean(false)
	assertEquals(false,bf)
	let bt = new Boolean(true)
	assertEquals(true,bt)

	let o1 = new Boolean(true)
	let o2 = new Boolean(true)
	assertNotSame(o1,o2)
}

// function call
{
	let b = Boolean()
	assertEquals(false,b)
	let bf = Boolean(false)
	assertEquals(false,bf)
	let bt = Boolean(true)
	assertEquals(true,bt)

	let o1 = Boolean(true)
	let o2 = Boolean(true)
	assertSame(o1,o2)
}

// Properties
{
	let bc = Boolean(true)
	assertThrows( () => bc.prop = 22 ); // strict mode

	let bo = new Boolean(true)
	bo.prop = 22
	assertEquals( 22, bo.prop )
}

// Check the prototype
assertEquals( Boolean.prototype, Object.getPrototypeOf(true))
