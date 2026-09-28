let a = 1

assertEquals( 1, a++ );
assertEquals( 2, a );
 
assertEquals( 3, ++a );
assertEquals( 3, a );

assertEquals( 3, a-- );
assertEquals( 2, a );
 
assertEquals( 1, --a );
assertEquals( 1, a );

let b1 = {a: 10}
assertEquals( 10, b1.a++ );
assertEquals( 11, b1.a );

let b2 = {a: 10}
assertEquals( 11, ++b2.a );
assertEquals( 11, b2.a );

let b3 = {a: 10}
assertEquals( 10, b3.a-- );
assertEquals( 9, b3.a );

let b4 = {a: 10}
assertEquals( 9, --b4.a );
assertEquals( 9, b4.a );

let nv0 = null
assertEquals( 0, nv0++)
let nv1 = null
assertEquals( 0, nv1--)

let ns0 = "88"
assertEquals( 88, ns0++)
let ns1 = "88"
assertEquals( 89, ++ns1)
let ns2 = "88"
assertEquals( 88, ns2--)
let ns3 = "88"
assertEquals( 87, --ns3)

{
	const o = {a: 1}
	assertEquals(1, o.a++)
	assertEquals(2, o.a)
	assertEquals(NaN, o.b++)
	assertEquals(NaN, o.b)
}

{
	let u1 = undefined
	assertEquals(NaN, u1++)
	assertEquals(NaN, u1)
	
	let u2 = undefined
	assertEquals(NaN, u2--)
	assertEquals(NaN, u2)
	
	let u3 = undefined
	assertEquals(NaN, ++u3)
	assertEquals(NaN, u3)
	
	let u4 = undefined
	assertEquals(NaN, --u4)
	assertEquals(NaN, u4)
}
{
	let u1 = null
	assertEquals(0, u1++)
	assertEquals(1, u1)
	
	let u2 = null
	assertEquals(0, u2--)
	assertEquals(-1, u2)
	
	let u3 = null
	assertEquals(1, ++u3)
	assertEquals(1, u3)
	
	let u4 = null
	assertEquals(-1, --u4)
	assertEquals(-1, u4)
}

// ASI: line terminator before ++/-- makes them prefix, not postfix
{
	let x = 10
	++x
	assertEquals(11, x)
}

// ASI: line terminator before ++/-- makes them prefix, not postfix
{
	let x = 10
	x -= 2
	++x
	assertEquals(9, x)
}
{
	let y = 5
	y += 1
	--y
	assertEquals(5, y)
}
