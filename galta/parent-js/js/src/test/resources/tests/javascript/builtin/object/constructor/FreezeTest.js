const o = {a: 1}
assertFalse( Object.isFrozen(o) )
assertTrue( Object.isExtensible(o) )

Object.freeze(o)
assertTrue( Object.isFrozen(o) )
assertFalse( Object.isExtensible(o) )

assertThrows( () => {o.a = 2} );
assertThrows( () => {o.b = 2} );
assertThrows( () => {delete o.a} );
assertEquals({a:1}, o)


const a = [88]
assertFalse( Object.isFrozen(a) )
assertTrue( Object.isExtensible(a) )
Object.freeze(a)
assertTrue( Object.isFrozen(a) )
assertFalse( Object.isExtensible(a) )

assertThrows( () => {a[0] = 99} );
assertThrows( () => {a[1] = 99} );
assertThrows( () => {delete a[0]} );
assertEquals([88], a)
