// argument is required 
try {
	Object.entries();
	fails();
} catch(e) {}


//
// Objects
//
const o = {}
assertEquals( [], Object.entries(o) );

const o1 = {a:'A'}
assertEquals( [['a','A']], Object.entries(o1) );

const o2 = {a:'A', b: 'B'}
assertEquals( [['a','A'],['b','B']], Object.entries(o2) );
