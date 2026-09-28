// argument is required 
try {
	Object.values();
	fails();
} catch(e) {}


//
// Objects
//
const o = {}
assertEquals( [], Object.values(o) );

const o1 = {a:'A'}
assertEquals( ['A'], Object.values(o1) );

const o2 = {a:'A', b: 'B'}
assertEquals( ['A','B'], Object.values(o2) );


//
// Arrays
//
const a = []
assertEquals( [], Object.values(a) );

const a1 = [1]
assertEquals( [1], Object.values(a1) );
