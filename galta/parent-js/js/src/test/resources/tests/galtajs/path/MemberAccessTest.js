const nul = null
const obj = {
	a: 1,
	b: 2,
	c: { a: 4, b:5 }
}

const arr = [
	1,
	2,
	[4,5],
	{ a:8, b:9}
]

//
// Object access
//

assertEquals(null, obj.z);
assertEquals(1, obj.a);
assertEquals(2, obj.b);
assertEquals({a:4,b:5}, obj.c);
assertEquals(null, obj.c.z);
assertEquals(4, obj.c.a);
assertEquals(5, obj.c.b);

// Optional chaining
assertEquals(null, null?.a);
assertEquals(null, nul?.a);
assertEquals(1, obj?.a);
assertEquals(null, obj?.a?.b);
assertEquals(4, obj?.c?.a);
assertEquals(null, obj?.c?.c);
assertEquals(4, obj ?. c ?. a);

// Optional chaining
assertEquals(null, null?.['a']);
assertEquals(null, nul?.['a']);
assertEquals(1, obj?.['a']);
assertEquals(null, obj?.['a']?.['b']);
assertEquals(4, obj?.['c']?.['a']);
assertEquals(null, obj?.['c']?.['c']);
assertEquals(4, obj ?. ['c'] ?.['a']);


//
// Array Access
//

assertEquals(null, arr[99]);
assertEquals(1, arr[0]);
assertEquals(2, arr[1]);
assertEquals([4,5], arr[2]);
assertEquals({a:8,b:9}, arr[3]);
assertEquals(null, arr[2][99]);
assertEquals(4, arr[2][0]);
assertEquals(5, arr[2][1]);

// Multiple levels
const a = [ [1,2], [3,4], [ [5,6], 7] ]
assertEquals(2, a[0][1] );
assertEquals(3, a[1][0] );
assertEquals(6, a[2][0][1] );
assertEquals(7, a[2][1] );

// Optional chaining
assertEquals(null, nul?.[0]);
assertEquals(null, null?.[0]);
assertEquals(1, arr?.[0]);
assertEquals(1, arr ?.[0]);


// Use integer members converted as strings
const o = {"0": 44, "1": 11, "22":222 }
assertEquals(44, o.0);
assertEquals(11, o.1);
assertEquals(222, o.22);
assertEquals(null, o.99);

// Should be able to use keywords in property names
const k = {for: 18, break: 24 }
assertEquals(18, k.for);
assertEquals(24, k.break);
