// argument is required 
try {
	Object.fromEntries();
	fails();
} catch(e) {}


const o = []
assertEquals( {}, Object.fromEntries(o) );

const o1 = [ [] ]
assertEquals( {}, Object.fromEntries(o1) );
console.log(Object.fromEntries(o1))
