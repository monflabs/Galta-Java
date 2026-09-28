let o1 = {a:1, y: 5}

assertTrue(o1.hasOwnProperty("a"))
assertTrue(o1.hasOwnProperty("y"))
assertTrue(o1.hasOwnProperty(['a']))

assertFalse(o1.hasOwnProperty("b"))
assertFalse(o1.hasOwnProperty(null))
assertFalse(o1.hasOwnProperty({}))
assertFalse(o1.hasOwnProperty([]))

let o2 = {a:1, y: 5, '[object Object]': 9}
assertTrue(o2.hasOwnProperty({}))

// Inherited properties return false
function Parent() { this.own = 1; }
Parent.prototype.inherited = 2;
const child = new Parent();
assertTrue(child.hasOwnProperty('own'));
assertFalse(child.hasOwnProperty('inherited'));

// Array: own index vs length
const arr = [10, 20, 30];
assertTrue(arr.hasOwnProperty(0));
assertTrue(arr.hasOwnProperty('length'));
assertFalse(arr.hasOwnProperty(5));

// Property added via defineProperty
const obj3 = {};
Object.defineProperty(obj3, 'hidden', { value: 1, enumerable: false });
assertTrue(obj3.hasOwnProperty('hidden')); // non-enumerable is still "own"

// Numeric keys coerced to string
const obj4 = { '0': 'zero', '1': 'one' };
assertTrue(obj4.hasOwnProperty(0));
assertTrue(obj4.hasOwnProperty('0'));
