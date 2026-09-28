// argument is required 
try {
	Object.keys();
	fails();
} catch(e) {}


//
// Objects
//
const o = {}
assertEquals( [], Object.keys(o) );

const o1 = {a:'A'}
assertEquals( ['a'], Object.keys(o1) );

const o2 = {a:'A', b: 'B'}
assertEquals( ['a','b'], Object.keys(o2) );

const o3 = {a:'A', b: 'B', '1': 'C'}
assertEquals( ['1','a','b'], Object.keys(o3) );

const o4 = {a:'A', '2': 'D', b: 'B', '1': 'C'}
assertEquals( ['1','2','a','b'], Object.keys(o4) );

//
// Arrays
//
const a = []
assertEquals( [], Object.keys(a) );

const a1 = [1]
assertEquals( ['0'], Object.keys(a1) );

const a2 = [1]
a2['Z'] = 3
assertEquals( ['0','Z'], Object.keys(a2) );
