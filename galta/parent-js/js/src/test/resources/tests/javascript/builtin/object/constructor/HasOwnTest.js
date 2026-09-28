// 2 arguments are required 
try {
	Object.hasOwn();
	fails();
} catch(e) {}
try {
	Object.hasOwn({});
	fails();
} catch(e) {}


const o = {}
assertFalse( Object.hasOwn(o,'a') );

const o1 = {a:'A'}
assertTrue( Object.hasOwn(o1,'a') );
assertFalse( Object.hasOwn(o1,'b') );
