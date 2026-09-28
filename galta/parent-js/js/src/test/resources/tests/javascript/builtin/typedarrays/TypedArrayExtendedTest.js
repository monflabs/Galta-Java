let b;

// Support mixed numbers
b = new Int8Array([1n,2m]);
assertEquals( 1, b[0])
assertEquals( 2, b[1])

b = new Uint8Array([1n,2m]);
assertEquals( 1, b[0])
assertEquals( 2, b[1])

b = new Int16Array([1n,2m]);
assertEquals( 1, b[0])
assertEquals( 2, b[1])

b = new Uint16Array([1n,2m]);
assertEquals( 1, b[0])
assertEquals( 2, b[1])

b = new Int32Array([1n,2m]);
assertEquals( 1, b[0])
assertEquals( 2, b[1])

b = new Uint32Array([1n,2m]);
assertEquals( 1, b[0])
assertEquals( 2, b[1])

if(typeof Float16Array!=='undefined') {
	b = new Float16Array([1n,2m]);
	assertEquals( 1, b[0])
	assertEquals( 2, b[1])
}

b = new Float32Array([1n,2m]);
assertEquals( 1, b[0])
assertEquals( 2, b[1])

b = new Float64Array([1n,2m]);
assertEquals( 1, b[0])
assertEquals( 2, b[1])

b = new BigInt64Array([1,2.2]);
assertEquals( 1, b[0])
assertEquals( 2, b[1])

b = new BigUint64Array([1,2.2]);
assertEquals( 1, b[0])
assertEquals( 2, b[1])

// Numeric-string elements must go through real ToBigInt conversion (not
// silently dropped/mis-typed by a generic ToNumeric coercion).
b = new BigInt64Array(["0","1"]);
assertEquals( 0n, b[0])
assertEquals( 1n, b[1])

b = new BigUint64Array(["2","3"]);
assertEquals( 2n, b[0])
assertEquals( 3n, b[1])

// The shared abstract %TypedArray% intrinsic: every concrete typed array
// constructor's [[Prototype]] is the SAME shared object (not
// Function.prototype), and that object's own "prototype" is the shared
// TypedArrayPrototype already reachable via Int8Array.prototype's chain.
const AbstractTypedArray = Object.getPrototypeOf(Int8Array);
assertEquals(AbstractTypedArray, Object.getPrototypeOf(Float64Array))
assertEquals(AbstractTypedArray, Object.getPrototypeOf(BigInt64Array))
assertEquals(AbstractTypedArray.prototype, Object.getPrototypeOf(Int8Array.prototype))
assertEquals(AbstractTypedArray, Object.getPrototypeOf(Int8Array.prototype).constructor)
assertFalse(Int8Array.hasOwnProperty('from'))
assertEquals('function', typeof Int8Array.from)
assertThrows( () => AbstractTypedArray() )
assertThrows( () => new AbstractTypedArray() )
