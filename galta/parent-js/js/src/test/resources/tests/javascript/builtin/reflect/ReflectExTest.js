// test/Reflect.test.js

// Check existence and type
assertNotUndefined(Reflect);
assertEquals(typeof Reflect, 'object');

// Standard Reflect methods as per ECMA-262
const methods = [
    'apply', 'construct', 'defineProperty', 'deleteProperty',
    'get', 'getOwnPropertyDescriptor', 'getPrototypeOf',
    'has', 'isExtensible', 'ownKeys', 'preventExtensions',
    'set', 'setPrototypeOf'
];

for (const method of methods) {
    assertTrue(method in Reflect);
    assertEquals(typeof Reflect[method], 'function');
}

// === apply ===
{
    const fn = function (a, b) { return a + b; };
    assertEquals(3, Reflect.apply(fn, null, [1, 2]));
    assertThrows(TypeError, () => Reflect.apply({}, null, []));
    assertThrows(TypeError, () => Reflect.apply(null, null, []));
}

// === construct ===
{
    function Foo(x) { this.x = x; }
    const obj = Reflect.construct(Foo, [42]);
    assertEquals(obj.x, 42);
    assertTrue(obj instanceof Foo);
    assertThrows(TypeError, () => Reflect.construct({}, []));
}

// === construct with a custom newTarget (GetPrototypeFromConstructor) ===
// Builtin (Java-implemented) constructors only - a plain user-defined
// function used as newTarget's target has a separate, still-open gap
// (see built-ins/Reflect/construct/return-with-newtarget-argument.js).
{
    const custom = { marker: true };
    function newTarget() {}
    newTarget.prototype = custom;

    assertEquals(custom, Object.getPrototypeOf(Reflect.construct(AggregateError, [[]], newTarget)));
    assertEquals(custom, Object.getPrototypeOf(Reflect.construct(WeakRef, [{}], newTarget)));
    assertEquals(custom, Object.getPrototypeOf(Reflect.construct(ArrayBuffer, [8], newTarget)));
    assertEquals(custom, Object.getPrototypeOf(Reflect.construct(DataView, [new ArrayBuffer(8)], newTarget)));
    assertEquals(custom, Object.getPrototypeOf(Reflect.construct(SharedArrayBuffer, [8], newTarget)));
    assertEquals(custom, Object.getPrototypeOf(Reflect.construct(Date, [], newTarget)));
    assertEquals(custom, Object.getPrototypeOf(Reflect.construct(Promise, [(res) => res(1)], newTarget)));
    assertEquals(custom, Object.getPrototypeOf(Reflect.construct(Int8Array, [4], newTarget)));
    assertEquals(custom, Object.getPrototypeOf(Reflect.construct(Int8Array, [[1, 2, 3]], newTarget)));

    // A newTarget with a non-object "prototype" falls back to the constructor's own default.
    function newTargetPrimitiveProto() {}
    newTargetPrimitiveProto.prototype = 42;
    assertEquals(AggregateError.prototype, Object.getPrototypeOf(Reflect.construct(AggregateError, [[]], newTargetPrimitiveProto)));
}

// === defineProperty ===
{
    const obj = {};
    const success = Reflect.defineProperty(obj, 'a', { value: 10 });
    assertTrue(success);
    assertEquals(obj.a, 10);
    assertThrows(TypeError, () => Reflect.defineProperty(null, 'a', {}));
}

// === deleteProperty ===
{
    const obj = { a: 1 };
    assertTrue(Reflect.deleteProperty(obj, 'a'));
    assertFalse('a' in obj);
    assertThrows(TypeError, () => Reflect.deleteProperty(null, 'a'));
}

// === get ===
{
    const obj = { x: 10 };
    assertEquals(10, Reflect.get(obj, 'x'));
    assertEquals(undefined, Reflect.get(obj, 'y'));
    assertThrows(TypeError, () => Reflect.get(null, 'x'));
}

// === getOwnPropertyDescriptor ===
{
    const obj = { x: 1 };
    const desc = Reflect.getOwnPropertyDescriptor(obj, 'x');
    assertEquals(desc.value, 1);
    assertTrue(desc.enumerable);
    assertThrows(TypeError, () => Reflect.getOwnPropertyDescriptor(null, 'x'));
}

// === getPrototypeOf ===
{
    const proto = {};
    const obj = Object.create(proto);
    assertEquals(Reflect.getPrototypeOf(obj), proto);
    assertThrows(TypeError, () => Reflect.getPrototypeOf(null));
}

// === has ===
{
    const obj = { x: 1 };
    assertTrue(Reflect.has(obj, 'x'));
    assertFalse(Reflect.has(obj, 'y'));
    assertThrows(TypeError, () => Reflect.has(null, 'x'));
}

// === isExtensible ===
{
    const obj = {};
    assertTrue(Reflect.isExtensible(obj));
    Object.preventExtensions(obj);
    assertFalse(Reflect.isExtensible(obj));
    assertThrows(TypeError, () => Reflect.isExtensible(null));
}

// === ownKeys ===
{
    const sym = Symbol('s');
    const obj = { a: 1 };
    obj[sym] = 2;
    const keys = Reflect.ownKeys(obj);
    assertTrue(keys.includes('a'));
    assertTrue(keys.includes(sym));
    assertThrows(TypeError, () => Reflect.ownKeys(null));
}

// === preventExtensions ===
{
    const obj = {};
    const result = Reflect.preventExtensions(obj);
    assertTrue(result);
    assertFalse(Object.isExtensible(obj));
    assertThrows(TypeError, () => Reflect.preventExtensions(null));
}

// === set ===
{
    const obj = { a: 1 };
    assertTrue(Reflect.set(obj, 'a', 2));
    assertEquals(obj.a, 2);
    assertThrows(TypeError, () => Reflect.set(null, 'a', 1));
}

// === setPrototypeOf ===
{
    const obj = {};
    const proto = {};
    assertTrue(Reflect.setPrototypeOf(obj, null));
    assertTrue(Reflect.setPrototypeOf(obj, proto));
    assertEquals(Object.getPrototypeOf(obj), proto);
    assertThrows(TypeError, () => Reflect.setPrototypeOf(null, {}));
    assertThrows(TypeError, () => Reflect.setPrototypeOf(42, {}));
    assertThrows(TypeError, () => Reflect.setPrototypeOf({}, undefined)); // not an object or null
    assertThrows(TypeError, () => Reflect.setPrototypeOf({}, 42)); // not an object or null
}
