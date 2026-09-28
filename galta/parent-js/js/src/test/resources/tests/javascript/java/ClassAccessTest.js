const ClassAccess = Java.type("tests.classes.ClassAccess");
const o = new ClassAccess();


//
// Static members
//
assertEquals("StaticField1",ClassAccess.S1)
assertEquals("StaticMethod2",ClassAccess.S2())
try {
	assertEquals("InstanceField1",ClassAccess.I1)
	fail();
} catch(e) {}
try {
	assertEquals("InstanceMethod2",ClassAccess.I2())
	fail();
} catch(e) {}


//
// Instance members
assertEquals("InstanceField1",o.I1)
assertEquals("InstanceMethod2",o.I2())
try {
	assertEquals("StaticField1",o.S1)
	fail();
} catch(e) {}
try {
	assertEquals("StaticMethod2",o.S2())
	fail();
} catch(e) {}

console.log("All passed");