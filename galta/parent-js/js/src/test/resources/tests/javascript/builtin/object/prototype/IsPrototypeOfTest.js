const o1 = Object.getPrototypeOf({});
const o2 = Object.getPrototypeOf({});
assertNotNull( o1 );
assertSame( o1, o2 );

// Basic prototype chain check
function Animal() {}
function Dog() {}
Dog.prototype = Object.create(Animal.prototype);

const d = new Dog();
assertTrue(Dog.prototype.isPrototypeOf(d));
assertTrue(Animal.prototype.isPrototypeOf(d));
assertFalse(d.isPrototypeOf(Dog.prototype));

// Object.prototype is prototype of all plain objects
assertTrue(Object.prototype.isPrototypeOf({}));
assertTrue(Object.prototype.isPrototypeOf([]));
assertTrue(Object.prototype.isPrototypeOf(new Map()));

// null-prototype objects have no chain
const nullProto = Object.create(null);
assertFalse(Object.prototype.isPrototypeOf(nullProto));

// Prototype of itself → false
const obj = {};
assertFalse(obj.isPrototypeOf(obj));

// Multi-level chain
const a = {};
const b = Object.create(a);
const c = Object.create(b);
assertTrue(a.isPrototypeOf(c));
assertTrue(b.isPrototypeOf(c));
assertFalse(c.isPrototypeOf(a));

// TypeError when called with null/undefined (non-object target is just "not found")
// Per spec, if V is not an object, return false (not TypeError)
assertFalse(Object.prototype.isPrototypeOf.call({}, null));
assertFalse(Object.prototype.isPrototypeOf.call({}, undefined));
