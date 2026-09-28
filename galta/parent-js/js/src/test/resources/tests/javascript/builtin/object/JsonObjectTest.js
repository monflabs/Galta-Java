// ctor
{
	let a = new Object()
	a.p = 'ff'
	assertEquals('ff',a.p)
}
{
	let a = new Object(true)
	assertEquals('object', typeof(a))
	let b = new Object(1.2)
	assertEquals('object', typeof(b))
	let c = new Object("abc")
	assertEquals('object', typeof(c))
	let o1 = {a: 23}
	let d = new Object(o1)
	assertTrue(typeof(d)=='object')
	assertTrue(o1===d)
}

{
	let a = {f: 'ff'}
	assertEquals('ff',a.f)
}

//
// Check a member access compared to a function call
//
const o1 = {
	toString: "ASTR"
}
assertEquals('ASTR',o1.toString)
assertEquals('[object Object]', Object.prototype.toString.call(o1) )

// Check the prototype
assertEquals( Object.prototype, Object.getPrototypeOf({}))
