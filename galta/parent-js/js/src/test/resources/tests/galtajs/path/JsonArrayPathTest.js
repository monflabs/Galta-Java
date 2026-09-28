//
// Access object members within Arrays
// It shows the use of sequences
// We disable the GaltaJS extension (see: JSEnvironment) so the array must be flatenized (*) before
//
{
	const a0$ = [
	]	
	assertUndefined(a0$['a'])
	assertEquals([], a0$.*['a'][] )
	assertEquals([undefined], [a0$['a']] )

	const a1$ = [
		{ a: 1 }
	]
	assertEquals(1,a1$.*.a)
	assertEquals(1,a1$[*]['a'])
	assertEquals([1], [a1$[*]['a']] )

	const a2$ = [
		{ a: 1 },
		{ b: 2 },
		{ a: 3 }
	]
	
	assertEquals([1,3],a2$.*.a)
	assertEquals([1,3],a2$[*].a[])
	
	// It is actually convenient to get an array lieral only adding the
	// values from a sequence. Can be none, 1 or multiple values
	assertEquals([1,3], [a2$.*.a] )
	assertEquals([2], [a2$.*.b] )
	assertEquals([], [a2$.*.c] )

	assertEquals([1,3], [a2$.*.a] )
	assertEquals(2,a2$.*.b)
	assertEquals(null,a2$.*.c)	
	assertEquals([1,3],a2$[*]['a'])
	assertEquals(2,a2$[*]['b'])
	assertEquals(null,a2$[*]['c'])
	
	assertEquals([1,3],a2$[*]['a'])

	assertEquals(3,a2$.*.a[][1])
	assertEquals(4,a2$.*.a[][1]+1)

	assertEquals(null,(a2$.*.a)[2])
}


//
// Object accessors
//
{
	const v$ = {
	  a: 11,
	  sort: 2,
	  b: 3
	}
	assertEquals(v$,{a: 11, sort: 2, b: 3});
	assertEquals(11,v$.a)
}


//
// Access inner object in an array
//
{
	const io1$ = [
		{ a: 11, b: 12, c:13 },
		{ a: 21, b: 22 }
	]
	assertEquals(null,io1$?.d);
	assertEquals(13,io1$.*.c);
	assertEquals([11,21],io1$.*.a);
	assertEquals([12,22],io1$.*.b);
}


//
// Recursive descent (search members)
//
{
	const a$ = [
		{ a: 11, b: 12, c:13 },
		{ a: 21, b: 22 },
		{ a: 31, b: 32, c: {a: 111, c: 113} }
	]
	assertEquals([11,21,31,111],a$..a)
	assertEquals([12,22,32],a$..b)
	assertEquals([13,{a: 111, c: 113},113],a$..c)
}

//
// Flatenizing array
//
{
	const fa1$ = [ [1,2], 3, [4,[5,6]] ]
	const fo1$ = {a: 12, b:32, c: 19 }
	assertEquals([1,2], fa1$[0].*);
	assertEquals([ [1,2], 3, [4,[5,6]] ],fa1$.*);
	assertEquals([12,32,19], fo1$.*);
}


//
// Array sort
//
{
	const a4$ = [ 23, 12, 45 ]
	assertEquals([12,23,45],a4$.sort());
	
	const a5$ = [
		[ 23, 12, 45 ],
		[ 43, 12, 33 ]
	]
	a5$[*].sort()
	assertEquals([12,23,45],a5$[0]);
	assertEquals([12,33,43],a5$[1]);
}
