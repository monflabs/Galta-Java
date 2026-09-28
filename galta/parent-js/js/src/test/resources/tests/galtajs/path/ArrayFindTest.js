//
// Member Find
//
const a$= [
	{ a: 11, b: 12, c:13, d: {a: 111, c:113} },
	{ a: 21, b: 22 },
	{ a: 31, b: 32, c: {a: 211, c: 213, d: {a:2111} } }
]

assertEquals([11,111,21,31,211,2111],a$..a)
assertEquals([12,22,32],a$..b)
assertEquals([13,113,{a:211,c:213,d:{a:2111}},213],a$..c)

const a1$ = jsonDeepClone(a$);
a1$..d.a = 'abc'
assertEquals(['abc','abc'],a1$..d.a)

const a2$ = jsonDeepClone(a$);
a2$..a = 999
assertEquals([{"a":999,"b":12,"c":13,"d":{"a":999,"c":113}},{"a":999,"b":22},{"a":999,"b":32,"c":{"a":999,"c":213,"d":{"a":999}}}],a2$)


//
// Index Find
//
const b$ = [
	[ 1, 2, 3, [4, 5] ],
	[ 6, 7 ],
	[ 8, 9, 10, [ 11, 12, [ 20, 21 ] ] ]
]

assertEquals([[ 1, 2, 3, [4, 5] ], 1,4,6,8,11,20],b$..[0])
assertEquals([[4,5], [ 11, 12, [ 20, 21 ] ]],b$..[3])

/*
// Assignement doesn't work as the numbers are processed, like 1, and we cannot assign them an array [x] value 
const b1$ = jsonDeepClone(b$);
assertEquals([ [1,2,3,[4,5]], [6,7], [8,9,10,[11,12,[20,21]]]],b1$)
b1$..[0] = 88
assertEquals([ 88, [88,7], [88,9,10,[88,12,[88,21]]]],b1$)

const b2$ = jsonDeepClone(b$);
b2$..[0] = [88,99]
assertEquals([ [88,99], [[88,99],7], [[88,99],9,10,[[88,99],12,[[88,99],21]]]],b2$)
*/