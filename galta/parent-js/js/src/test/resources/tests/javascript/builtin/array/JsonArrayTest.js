// ctor
{
	let a = new Array()
	assertEquals(0,a.length)
	
	let a1 = new Array(4)
	assertEquals(4,a1.length)
	
	let a2 = new Array(new Number(4))
	assertEquals(1,a2.length)
	assertEquals(4,a2[0])
	
	let a3 = new Array(4,3)
	assertEquals(2,a3.length)
	assertEquals(4,a3[0])
	assertEquals(3,a3[1])
}

{
	let a = [2,4]
	assertEquals(2,a.length)
	assertEquals(2,a[0])
	assertEquals(4,a[1])
}

// Check the prototype
assertEquals( Array.prototype, Object.getPrototypeOf([]))

{
	// Create a custom prototype that inherits from Array.prototype
	const customProto = Object.create(Array.prototype);
	Object.defineProperty(customProto, "1", {
	  get() {
	    return this._valueAt1;
	  },
	  set(value) {
	    this._valueAt1 = value;
	  },
	  configurable: true,
	  enumerable: true
	});

	// Create an array and set its prototype
	const arr = [1,2,3];
	Object.setPrototypeOf(arr, customProto);
	arr[1] = "Hello";
	assertEquals( undefined, arr._valueAt1 )
	assertEquals( [1,'Hello',3], arr )
	assertEquals( 1, arr[0] )
	assertEquals( 'Hello', arr[1] )
	assertEquals( 3, arr[2] )
	
	const arr2 = [1,,3];
	Object.setPrototypeOf(arr2, customProto);
	arr2[1] = "Hello";
	assertEquals( 'Hello', arr2._valueAt1 )
	assertEquals( 1, arr2[0] )
	assertEquals( 'Hello', arr2[1] )
	assertEquals( 3, arr2[2] )
}