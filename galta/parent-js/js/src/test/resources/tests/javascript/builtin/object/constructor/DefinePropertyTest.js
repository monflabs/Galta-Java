const obj = [];

Object.defineProperty(obj, "un", {
});
assertEquals( false, Object.getOwnPropertyDescriptor(obj,"un").enumerable )
assertEquals( false, Object.getOwnPropertyDescriptor(obj,"un").configurable )
assertEquals( false, Object.getOwnPropertyDescriptor(obj,"un").writable )
assertEquals(undefined,obj.un);


Object.defineProperty(obj, "a", {
  value: 1,
});
assertEquals( false, Object.getOwnPropertyDescriptor(obj,"a").enumerable )
assertEquals( false, Object.getOwnPropertyDescriptor(obj,"a").configurable )
assertEquals( false, Object.getOwnPropertyDescriptor(obj,"a").writable )
assertEquals(1,obj.a);


Object.defineProperty(obj, "b", {
  enumerable: false,
  configurable: false,
  writable: false,
  value: 2,
});
assertEquals( false, Object.getOwnPropertyDescriptor(obj,"b").enumerable )
assertEquals( false, Object.getOwnPropertyDescriptor(obj,"b").configurable )
assertEquals( false, Object.getOwnPropertyDescriptor(obj,"b").writable )
assertEquals(2,obj.b);


Object.defineProperty(obj, "c", {
  enumerable: true,
  configurable: false,
  writable: false,
  value: 3,
});
assertEquals( true, Object.getOwnPropertyDescriptor(obj,"c").enumerable )
assertEquals( false, Object.getOwnPropertyDescriptor(obj,"c").configurable )
assertEquals( false, Object.getOwnPropertyDescriptor(obj,"c").writable )
assertEquals(3,obj.c);


Object.defineProperty(obj, "d", {
  enumerable: true,
  configurable: true,
  writable: false,
  value: 4,
});
assertEquals( true, Object.getOwnPropertyDescriptor(obj,"d").enumerable )
assertEquals( true, Object.getOwnPropertyDescriptor(obj,"d").configurable )
assertEquals( false, Object.getOwnPropertyDescriptor(obj,"d").writable )
assertEquals(4,obj.d);


Object.defineProperty(obj, "e", {
  enumerable: true,
  configurable: true,
  writable: true,
  value: 5,
});
assertEquals( true, Object.getOwnPropertyDescriptor(obj,"e").enumerable )
assertEquals( true, Object.getOwnPropertyDescriptor(obj,"e").configurable )
assertEquals( true, Object.getOwnPropertyDescriptor(obj,"e").writable )
assertEquals(5,obj.e);


//
// Getter & Setter
//

assertThrows( TypeError, () => Object.defineProperty(obj, "f", {value: 5, get: () => ""}) );
assertThrows( TypeError, () => Object.defineProperty(obj, "f", {value: 5, set: () => ""}) );
assertThrows( TypeError, () => Object.defineProperty(obj, "f", {writable: true, get: () => ""}) );
assertThrows( TypeError, () => Object.defineProperty(obj, "f", {writable: false, set: () => ""}) );

Object.defineProperty(obj, "f", {
  get: () => {
	return "ff"
  }
});
assertEquals("ff",obj.f);

Object.defineProperty(obj, "g", {
  get: function () {
	return this.gg
  },
  set: function (v) {
    this.gg = v;
  }
});
assertEquals(undefined,obj.g);
assertEquals(undefined,obj.gg);
obj.g = 678 
console.log(obj)
assertEquals(678,obj.g);
assertEquals(678,obj.gg);


//
// On primitives
// Works on object but fails on non objects
//

const num = new Number(36);
Object.defineProperty(num, "abc", {
  enumerable: true,
  configurable: false,
  writable: false,
  value: "def",
});
assertEquals( true, Object.getOwnPropertyDescriptor(num,"abc").enumerable )
assertEquals( false, Object.getOwnPropertyDescriptor(num,"abc").configurable )
assertEquals( false, Object.getOwnPropertyDescriptor(num,"abc").writable )
assertEquals("def",num.abc);

assertThrows( () => {
	Object.defineProperty(12, "abc", {
	  value: "def",
	});
});


//
// Object.defineProperty throws TypeError when the redefinition is rejected
// (spec: DefinePropertyOrThrow), instead of silently doing nothing.
//

const h = {};
Object.defineProperty(h, "x", {value: 1, writable: true, configurable: false, enumerable: true});

// Can't flip configurable back to true on a non-configurable property.
assertThrows( TypeError, () => Object.defineProperty(h, "x", {configurable: true}) );
assertEquals(1, h.x);

// While still writable (even if non-configurable), value and writable itself may
// still be changed freely -- only once writable:false is set does the value freeze.
Object.defineProperty(h, "x", {value: 2});
assertEquals(2, h.x);
Object.defineProperty(h, "x", {writable: false});
assertEquals(2, h.x);
assertEquals(false, Object.getOwnPropertyDescriptor(h, "x").writable);

// Now that it's both non-configurable and non-writable, changing the value throws.
assertThrows( TypeError, () => Object.defineProperty(h, "x", {value: 3}) );
assertEquals(2, h.x);
// ...but redefining with the *same* value is a no-op success, not a failure.
Object.defineProperty(h, "x", {value: 2});
assertEquals(2, h.x);


//
// Getter/setter on a numeric array index
//

{
	const arr = [1,2,3];
	const log = [];
	let getCount = 0;
	Object.defineProperty(arr, 0, {
		get() { getCount++; return "G" + getCount; },
		set(v) { log.push("set:" + v); },
		enumerable: true,
		configurable: true
	});
	assertEquals("G1", arr[0]);
	assertEquals("G2", arr[0]);
	arr[0] = 42;
	assertEquals(1, log.length);
	assertEquals("set:42", log[0]);
	assertEquals(3, arr.length);

	// for-of / spread / values() must invoke the getter, not read raw storage.
	const seen = [];
	for(const v of arr) seen.push(v);
	assertEquals("G3,2,3", seen.join(","));
	assertEquals("G4,2,3", [...arr].join(","));

	// Defining an accessor beyond the current length grows it, with no raw
	// value stored at the intervening holes.
	const arr2 = [1,2,3];
	Object.defineProperty(arr2, 5, { get() { return "X"; }, enumerable: true });
	assertEquals(6, arr2.length);
	assertEquals("X", arr2[5]);
	assertEquals(undefined, arr2[4]);
	assertEquals(true, 5 in arr2);
	assertEquals(false, 4 in arr2);

	// Object.keys/for-in must include an accessor-only index.
	assertEquals("0,1,2,5", Object.keys(arr2).join(","));

	// Object.getOwnPropertyDescriptors must report the accessor shape
	// WITHOUT invoking the getter as a side effect.
	const arr3 = [1,2,3];
	let descCalls = 0;
	Object.defineProperty(arr3, 0, { get() { descCalls++; return 1; }, configurable: true, enumerable: true });
	const descs = Object.getOwnPropertyDescriptors(arr3);
	assertEquals(0, descCalls);
	assertEquals("function", typeof descs[0].get);

	// delete then redefine as a plain data property.
	delete arr3[0];
	assertEquals(false, 0 in arr3);
	arr3[0] = "plain";
	assertEquals("plain", arr3[0]);

	// A sealed/non-extensible array rejects a brand-new accessor.
	const sealedArr = [1,2,3];
	Object.seal(sealedArr);
	assertThrows( TypeError, () => Object.defineProperty(sealedArr, 0, { get() { return 1; } }) );

	const frozenBeyond = Object.preventExtensions([1,2,3]);
	assertThrows( TypeError, () => Object.defineProperty(frozenBeyond, 5, { get() { return 1; } }) );

	// Setting through an accessor with no setter throws in strict mode.
	const getOnly = [1,2,3];
	Object.defineProperty(getOnly, 0, { get() { return "ro"; }, configurable: true });
	assertThrows( TypeError, () => { getOnly[0] = 99; } );
}