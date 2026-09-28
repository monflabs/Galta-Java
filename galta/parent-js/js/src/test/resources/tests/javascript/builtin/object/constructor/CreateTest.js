assertEquals({}, Object.create())

const o1 = Object.create({a:1})
assertEquals({}, o1)
assertEquals(1, o1.a)

const p1 = Object.getPrototypeOf(o1)
assertEquals(1, p1.a)

// Object.create(null) — no prototype
const noProto = Object.create(null);
assertEquals(null, Object.getPrototypeOf(noProto));
noProto.x = 42;
assertEquals(42, noProto.x);
// hasOwnProperty is not available on prototype-less objects (inherited from Object.prototype)
assertFalse('hasOwnProperty' in noProto);
// Use Object.prototype.hasOwnProperty.call() to check own properties on null-proto objects
assertTrue(Object.prototype.hasOwnProperty.call(noProto, 'x'));
assertFalse(Object.prototype.hasOwnProperty.call(noProto, 'y'));

// Object.create with property descriptors
const o2 = Object.create({}, {
    x: { value: 10, writable: true, enumerable: true, configurable: true },
    y: { value: 20, enumerable: false }
});
assertEquals(10, o2.x);
assertEquals(20, o2.y);
assertEquals(false, Object.getOwnPropertyDescriptor(o2, 'y').enumerable);
