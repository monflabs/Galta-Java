const o = {a: 1}
assertFalse( Object.isSealed(o) )
assertTrue( Object.isExtensible(o) )

Object.seal(o)
assertTrue( Object.isSealed(o) )
assertFalse( Object.isExtensible(o) )

o.a = 2;
assertThrows( () => {o.b = 2} );
assertThrows( () => {delete o.a} );
assertEquals({a:2}, o)


const a = [88]
assertFalse( Object.isSealed(a) )
assertTrue( Object.isExtensible(a) )
Object.seal(a)
assertTrue( Object.isSealed(a) )
assertFalse( Object.isExtensible(a) )

a[0] = 99;
assertThrows( () => {a[1] = 99} );
assertThrows( () => {delete a[0]} );
assertEquals([99], a)
