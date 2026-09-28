//
// Object ctor
//

// Destruct java objects
const PObject1 = Java.type("tests.classes.PObject1")
const PObject2 = Java.type("tests.classes.PObject2")
function f({a1,a2}) {
	assertEquals("XX",a1);	
	assertEquals("YY",a2.b1);	
	assertEquals("ZZ",a2.b2);	
}
f( new PObject1("XX",new PObject2("YY","ZZ")))


// Destruct java array
function g([a1,a2]) {
	assertEquals("XX",a1);	
	assertEquals("YY",a2[0]);	
	assertEquals("ZZ",a2[1]);	
}

const Object = Java.type("java.lang.Object")

const o2 = new Object[2]; o2[0]="YY"; o2[1]="ZZ";
const o1 = new Object[2]; o1[0]="XX"; o1[1]=o2;
g(o1)
//g( new Object[ ["XX",new Object[ ["YY","ZZ"]] ] )
