const a = [
	{ a: 1, b: 10, c: {v: 100} },
	{ a: 2, b: 20, c: {v: 200} },
	{ a: 3, b: 30, c: {v: 300} },
]

//
// Simple mapping
//
const f1 = a[*].(@.a)
assertEquals([1,2,3],f1)

const f2 = a[*].(@.a*@.b)
assertEquals([10,40,90],f2)

const f3 = a[*].(@.a*@.c.v)
assertEquals([100,400,900],f3)


//
// Map as functions
//
function times100() {return this.a*100; } 
assertEquals([100,200,300],a[*].(times100))


const s = [
	{v: "AbC"},
	{v: "deF"},
	{v: "GHi"},
]

assertEquals(["ABC","DEF","GHI"], s[*].v.toUpperCase())
assertEquals(["ABC","DEF","GHI"], s[*].v.(@.toUpperCase()))
assertEquals(["ABC","DEF","GHI"], s[*].v.(String.prototype.toUpperCase))
