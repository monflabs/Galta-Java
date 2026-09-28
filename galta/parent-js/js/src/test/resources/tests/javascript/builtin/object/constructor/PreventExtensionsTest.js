const o = {a: 1}
assertTrue( Object.isExtensible(o) )

Object.preventExtensions(o)
assertFalse( Object.isExtensible(o) )

o.a = 2;
assertThrows( () => {o.b = 2} );
delete o.a;
assertEquals({}, o)


const a = [88]
assertTrue( Object.isExtensible(a) )
Object.preventExtensions(a)
assertFalse( Object.isExtensible(a) )

a[0] = 99;
assertThrows( () => {a[1] = 99} );
delete a[0];
assertEquals([,], a)
