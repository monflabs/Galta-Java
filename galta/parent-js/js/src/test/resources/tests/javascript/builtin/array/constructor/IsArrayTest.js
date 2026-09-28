assertTrue( Array.isArray([]) );
assertTrue( Array.isArray([1,2.3]) );
assertFalse( Array.isArray({}) );
assertFalse( Array.isArray({a: 1}) );
assertFalse( Array.isArray(1) );
assertFalse( Array.isArray("ABC") );
assertFalse( Array.isArray(true) );

const List =Java.type("java.util.ArrayList")
assertFalse( Array.isArray(new List()) );

// Spec's IsArray: a Proxy must be resolved to its ultimate target
// (recursively - a Proxy may wrap another Proxy), and a revoked proxy in
// the chain is itself a TypeError, not just "not an array".
assertTrue( Array.isArray(new Proxy([], {})) );
assertTrue( Array.isArray(new Proxy(new Proxy([], {}), {})) );
assertFalse( Array.isArray(new Proxy({}, {})) );
{
    const revocable = Proxy.revocable([], {});
    revocable.revoke();
    assertThrows(TypeError, () => Array.isArray(revocable.proxy));
}
