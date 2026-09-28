const object1 = {};
const array1 = [];
object1.property1 = 42;
array1[0] = 42;

assertEquals(true,object1.propertyIsEnumerable('property1'))
assertEquals(true,array1.propertyIsEnumerable(0))
assertEquals(false,array1.propertyIsEnumerable('length'))

// Non-existent property returns false
assertFalse(object1.propertyIsEnumerable('missing'));

// Inherited properties return false (only own)
function Parent() {}
Parent.prototype.inherited = true;
const child = new Parent();
assertFalse(child.propertyIsEnumerable('inherited'));

// Non-enumerable own property returns false
const obj2 = {};
Object.defineProperty(obj2, 'hidden', { value: 1, enumerable: false });
assertFalse(obj2.propertyIsEnumerable('hidden'));

// Enumerable own property returns true
const obj3 = {};
Object.defineProperty(obj3, 'visible', { value: 1, enumerable: true });
assertTrue(obj3.propertyIsEnumerable('visible'));

// Array: element is enumerable, length is not
const arr2 = [1, 2, 3];
assertTrue(arr2.propertyIsEnumerable(0));
assertTrue(arr2.propertyIsEnumerable(1));
assertFalse(arr2.propertyIsEnumerable('length'));
assertFalse(arr2.propertyIsEnumerable(5)); // out of bounds

// Numeric index coerced to string for object
const obj4 = { '0': 'zero' };
assertTrue(obj4.propertyIsEnumerable(0));
assertTrue(obj4.propertyIsEnumerable('0'));
