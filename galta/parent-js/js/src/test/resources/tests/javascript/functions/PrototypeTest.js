function Point(x,y) {
	this.x = x;
	this.y = y;
}
Point.prototype.sum = function() { return this.x + this.y }

const p = new Point(1,2);

assertEquals( 1, p.x );
assertEquals( 2, p.y );
assertEquals( 3, p.sum() );


{
	const p = { f: 12 }
	const o = {}
	Object.setPrototypeOf(o,p)
	assertEquals(12, o.f)  
}

{
	const p1 = { f: 34, ff: 1, get g() {return this.ff;} }
 	const p2 = { ff: 2}
	Object.setPrototypeOf(p2,p1)
	const o = { ff: 3}
	Object.setPrototypeOf(o,p2)
	assertEquals(34, o.f)  
	
	assertEquals(1,p1.g);
	assertEquals(2,p2.g);
	assertEquals(3,o.g);
}


{
	const symb = Symbol();
	const p1 = { [symb]: 33 }
	const p2 = { }
	Object.setPrototypeOf(p2,p1)
	const o = {}
	Object.setPrototypeOf(o,p2)
	assertEquals(undefined, o.symb)  
	assertEquals(33, o[symb])  
}


// Function prototype is a Function
assertEquals("function", typeof Function.prototype);
assertTrue(Function instanceof Function);
