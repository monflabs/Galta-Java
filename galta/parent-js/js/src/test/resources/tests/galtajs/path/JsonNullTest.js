assertEquals( null, null );

const a = {
	b: 11,
	c: {
		d: 12
	}
}

//
// Null propagation
assertUndefined( a.e )
assertUndefined( a.b.x )
assertThrows( () => a.e.f )
assertUndefined( a?.e?.f )

assertUndefined( a['e'] )
assertThrows( () => a['e']['f'] )
assertUndefined( a['c']['e'] )


//
// With non JsonObject...

const HashMap = Java.type("java.util.HashMap");
const map = new HashMap();
map.set("a",{b: 1})
map.set("b",3)

assertEquals( 1, map.get("a").b )
assertThrows( () => {
	const v = map.get("b").get("c");
});
