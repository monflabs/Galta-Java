// Object
const target = { a: 1, b: 2 };
const source = { b: 4, c: 5 };
const returnedTarget = Object.assign(target, source);
assertEquals( { a: 1, b: 4, c: 5 }, target);
assertEquals( { a: 1, b: 4, c: 5 }, returnedTarget);


assertEquals( {a:1, b:2, c:3}, Object.assign({}, {a:1, b:2, c:3}) );
assertEquals( {a:1, b:2, c:3}, Object.assign({a:1}, {b:2, c:3}) );
assertEquals( {a:1, b:2, c:3}, Object.assign({a:1, b:2}, {c:3}) );
assertEquals( {a:1, b:2, c:3}, Object.assign({a:1}, null, {b:2, c:3}) );

try {
	assertEquals( {a:1, b:2, c:3}, Object.assign(null, {a:1, b:2, c:3}) );
	fail();
} catch(e){}


// Array
const a = [10, 20]
assertEquals(
	{
	  value: 10,
	  writable: true,
	  enumerable: true,
	  configurable: true
	},
	Object.getOwnPropertyDescriptor(a,'0') );
assertEquals(
	{
	  value: 10,
	  writable: true,
	  enumerable: true,
	  configurable: true
	},
	Object.getOwnPropertyDescriptor(a,0) );	
assertEquals(
	{
	  value: 20,
	  writable: true,
	  enumerable: true,
	  configurable: true
	},
	Object.getOwnPropertyDescriptor(a,'1') );
assertEquals(
	{
	  value: 20,
	  writable: true,
	  enumerable: true,
	  configurable: true
	},
	Object.getOwnPropertyDescriptor(a,1) );	
	
assertEquals(
	{
	  value: 2,
	  writable: true,
	  enumerable: false,
	  configurable: false
	},
	Object.getOwnPropertyDescriptor(a,'length') );	
