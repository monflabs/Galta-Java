let a = new Object()
a.a = 'AA'
a.b = 'AB'
a.c = new Object()
a.c.a = 'AC'

let b = new Object()
b.a = 'BA'
b.b = 'BB'

// No context is available
assertEquals({a:'AA',b:'AB',c:{a:'AC'}},a)

f() // Check the function


with(a) {
	assertEquals('AA',a)
	f() // Check the function
	g() // Check the function
	{ 
		// Inside a block, should be propagated
		assertEquals('AA',a)
	}
	// inner block
	with(b) {
		f() // Check the function
		g() // Check the function
		assertEquals('AA',a)
	}
	with(c) {
		g() // Check the function
		g() // Check the function
		assertEquals('AC',a)
	}
	
	// Should be restored after the inner assertEquals('AA',a)
  	function g() {
		assertEquals('AA',a)
  	}
}


// No context is available
assertEquals({a:'AA',b:'AB',c:{a:'AC'}},a)


function f() {
	assertEquals({a:'AA',b:'AB',c:{a:'AC'}},a)
}


// Closures
var c1, c2; 
with (7) { c1 = valueOf(); c2 = toString() } 
assertEquals( 7, c1);
assertEquals( "7", c2);



//
// Updates
//

with(a) {
	b = 'BB'
}
assertEquals({a:'AA',b:'BB',c:{a:'AC'}},a)

with(a) {
	b = 'B1'
	with(c) {
		a = 'AAA'
	}
}
assertEquals({a:'AA',b:'B1',c:{a:'AAA'}},a)

with(a) {
	delete b
}
assertEquals({a:'AA',c:{a:'AAA'}},a)


//
// A `var x = ...` declaration's assignment resolves `x` the normal way (not
// straight into the declaration/global scope), so a with-object property of
// the same name shadows it: the write lands on shadowingObj.shadowed, and
// the hoisted global var itself stays untouched (undefined).
//
var shadowed;
var shadowingObj = {shadowed: 'fromWith'};
with(shadowingObj) {
	var shadowed = 'assigned';
}
assertEquals( 'assigned', shadowingObj.shadowed );
assertEquals( undefined, shadowed );

//
// typeof
//
with(a) {
	assertEquals("string",typeof a)
	assertEquals("object",typeof c)
	assertEquals("string",typeof c.a)
	assertEquals("undefined",typeof zz)
}
