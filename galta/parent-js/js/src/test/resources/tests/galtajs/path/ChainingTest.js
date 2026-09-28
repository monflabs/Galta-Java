const obj = {
	a: 1,
	b: 2,
	c: { a: 4, b:5 },
	
	e: () => 11,
	f: () => { return {g: () => { return {a:22} } } }
}

//
// Member Access
assertEquals( 1, obj.a )
assertEquals( 1, obj?.a )

assertEquals( 4, obj.c.a )
assertEquals( null, obj.d?.a.e )

assertEquals( null, obj.z?.a )
assertThrows( () => obj.z.a )


//
// Array Member Access
assertEquals( 1, obj['a'] )
assertEquals( 1, obj?.['a'] )

assertEquals( 4, obj['c']['a'] )
assertEquals( null, obj['d']?.['a']['e'] )

assertEquals( null, obj['z']?.['a'] )
assertThrows( () => obj['z']['a'] )


//
// Call Access
assertEquals( 11, obj.e() )
assertEquals( 11, obj.e?.() )

assertThrows( () => obj.e2() )
assertEquals( null, obj.e2?.() )

assertEquals( {a:22}, obj.f().g() )
assertEquals( {a:22}, obj.f?.().g() )
assertEquals( {a:22}, obj?.f?.()?.g?.() )
assertEquals( null, obj.f2?.().g() )

assertEquals( 22, obj.f().g().a )
assertEquals( 22, obj.f().g?.().a )
assertEquals( null, obj.f().gg?.().a )
assertEquals( null, obj.f().gg?.()?.a )
assertEquals( null, obj.f().g?.().b )

assertEquals( null, obj.f().gg?.().a )
assertEquals( null, obj.f().gg?.()?.a )
