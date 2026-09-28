{
	assertEquals(1, Reflect.apply(Math.floor, undefined, [1.75]));
	assertEquals("hello", Reflect.apply(String.fromCharCode, undefined, [104, 101, 108, 108, 111]))

	assertEquals(4, Reflect.apply(RegExp.prototype.exec, /ab/, ["confabulation"]).index );

	assertEquals("i", Reflect.apply("".charAt, "ponies", [3]));
}

{
	function func1(a, b, c) {
	  this.sum = a + b + c;
	}

	const args = [1, 2, 3];
	const object1 = new func1(...args);
	const object2 = Reflect.construct(func1, args);

	assertEquals(6, object2.sum);
	assertEquals(6, object1.sum);
}

{
	const object1 = {};

	assertEquals( true, Reflect.defineProperty(object1, "property1", { value: 42 }));
	assertEquals( 42, object1.property1);
}

{
	const object1 = {
	  property1: 42,
	};

	assertEquals( true, Reflect.deleteProperty(object1, "property1") );
	assertEquals( undefined, object1.property1);

	const array1 = [1, 2, 3, 4, 5];
	assertEquals( true, Reflect.deleteProperty(array1, "3") );

	assertEquals( [1,2,3,,5], array1);
}

{
	const object1 = {
	  x: 1,
	  y: 2,
	};

	assertEquals( 1, Reflect.get(object1, "x"));

	const array1 = ["zero", "one"];
	assertEquals( "one", Reflect.get(array1, 1));
	
	const proto = { inheritedProp: 'inherited' };
	const obj = Object.create(proto);
	obj.ownProp = 'own';

	assertEquals("own", Reflect.get(obj, 'ownProp')); 
	assertEquals("inherited", Reflect.get(obj, 'inheritedProp')); 	
}

{
	const object1 = {
	  property1: 42,
	};

	assertEquals(42, Reflect.getOwnPropertyDescriptor(object1, "property1").value);
	assertEquals(undefined, Reflect.getOwnPropertyDescriptor(object1, "property2"));
	assertEquals(true, Reflect.getOwnPropertyDescriptor(object1, "property1").writable);
}

{
	const object1 = {
	  property1: 42,
	};

	const proto1 = Reflect.getPrototypeOf(object1);

	assertSame( Object.prototype, proto1);
	assertEquals( null, Reflect.getPrototypeOf(proto1));
}

{
	const object1 = {
	  property1: 42,
	};

	assertEquals(true, Reflect.has(object1, "property1"));
	assertEquals(false, Reflect.has(object1, "property2"));
	assertEquals(true, Reflect.has(object1, "toString"));
	
	const proto = { inheritedProp: 'inherited' };
	const obj = Object.create(proto);
	obj.ownProp = 'own';

	assertEquals(true, Reflect.has(obj, 'ownProp')); 
}

{
	const object1 = {};

	assertEquals(true, Reflect.isExtensible(object1));

	Reflect.preventExtensions(object1);
	assertEquals(false, Reflect.isExtensible(object1));

	const object2 = Object.seal({});
	assertEquals(false, Reflect.isExtensible(object2));
}

{
	const object1 = {
	  property1: 42,
	  property2: 13,
	};

	const array1 = [];

	assertEquals(["property1", "property2"], Reflect.ownKeys(object1));
	assertEquals(["length"], Reflect.ownKeys(array1));
}

{
	const object1 = {};
	assertEquals( true, Reflect.set(object1, "property1", 42) );
	assertEquals( 42, object1.property1);

	Object.seal(object1)
	assertEquals( true, Reflect.set(object1, "property1", 44) );
	assertEquals( 44, object1.property1);

	Object.freeze(object1)
	assertEquals( false, Reflect.set(object1, "property1", 46) );
	assertEquals( 44, object1.property1);

	const array1 = ["duck", "duck", "duck"];
	assertEquals(true, Reflect.set(array1, 2, "goose") );
	assertEquals( "goose", array1[2]);
}

{
	const object1 = {};
	assertEquals(true, Reflect.setPrototypeOf(object1, Object.prototype));
	assertEquals(true, Reflect.setPrototypeOf(object1, null));

	const object2 = {};
	assertEquals(false, Reflect.setPrototypeOf(Object.freeze(object2), null));

	// SameValue(V,current) short-circuits even on a non-extensible object.
	const object3 = {};
	Object.preventExtensions(object3);
	assertEquals(true, Reflect.setPrototypeOf(object3, Object.prototype));

	// Cycle detection: setting proto to self, or to a descendant, must fail.
	const object4 = {};
	assertEquals(false, Reflect.setPrototypeOf(object4, object4));
	assertEquals(Object.prototype, Object.getPrototypeOf(object4));

	const object5 = {};
	const object6 = Object.create(object5);
	assertEquals(false, Reflect.setPrototypeOf(object5, object6));
	assertEquals(Object.prototype, Object.getPrototypeOf(object5));
}

// Reflect.* target must be an object - not just non-null/undefined.
{
	assertThrows(TypeError, () => Reflect.get(5, "x"));
	assertThrows(TypeError, () => Reflect.get(Symbol(), "x"));
	assertThrows(TypeError, () => Reflect.set(5, "x", 1));
	assertThrows(TypeError, () => Reflect.has(5, "x"));
	assertThrows(TypeError, () => Reflect.ownKeys(5));
	assertThrows(TypeError, () => Reflect.isExtensible(5));
	assertThrows(TypeError, () => Reflect.preventExtensions(5));
	assertThrows(TypeError, () => Reflect.getPrototypeOf(5));
	assertThrows(TypeError, () => Reflect.setPrototypeOf(5, null));
	assertThrows(TypeError, () => Reflect.getOwnPropertyDescriptor(5, "x"));
	assertThrows(TypeError, () => Reflect.defineProperty(5, "x", {}));
	assertThrows(TypeError, () => Reflect.deleteProperty(5, "x"));
}

// Reflect.set with an explicit receiver different from the target.
{
	const target = {};
	const receiver = {};
	assertEquals(true, Reflect.set(target, "p", 43, receiver));
	assertEquals(undefined, Object.getOwnPropertyDescriptor(target, "p"));
	assertEquals(43, receiver.p);

	// Target has an inherited accessor: the setter is invoked with `receiver` as `this`.
	let seenThis, seenValue;
	const base = {};
	Object.defineProperty(base, "q", { set(v) { seenThis = this; seenValue = v; } });
	const derived = Object.create(base);
	const r2 = {};
	assertEquals(true, Reflect.set(derived, "q", 99, r2));
	assertEquals(r2, seenThis);
	assertEquals(99, seenValue);

	// Receiver's own accessor blocks a data write from the target side.
	const t2 = {};
	const r3 = {};
	Object.defineProperty(r3, "p", { set: function() {} });
	assertEquals(false, Reflect.set(t2, "p", 1, r3));

	// Receiver's own non-writable data property blocks the write.
	const t3 = {};
	const r4 = {};
	Object.defineProperty(r4, "p", { value: 1, writable: false });
	assertEquals(false, Reflect.set(t3, "p", 2, r4));
	assertEquals(1, r4.p);

	// A non-object receiver fails gracefully (returns false, doesn't throw).
	assertEquals(false, Reflect.set({}, "p", 1, "not an object"));
}

// Reflect.ownKeys skips holes in a sparse array.
{
	assertEquals(JSON.stringify(["2", "length"]), JSON.stringify(Reflect.ownKeys([, , 2])));
	assertEquals(JSON.stringify(["length"]), JSON.stringify(Reflect.ownKeys([])));
}

// Reflect.getOwnPropertyDescriptor invokes a Proxy's own trap.
{
	let trapCalled = false;
	const target = {};
	const p = new Proxy(target, {
		getOwnPropertyDescriptor(t, key) {
			trapCalled = true;
			return undefined;
		}
	});
	Reflect.getOwnPropertyDescriptor(p, "x");
	assertTrue(trapCalled);
}
