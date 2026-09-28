const PropertyAccess = Java.type("tests.classes.PropertyAccess");
const o = new PropertyAccess();

// Read only property
const v1 = o.pr;
assertEquals("p-pr",v1)
try {
  o.pr = "aaa"
  fail();
} catch(e) {}
assertEquals("p-pr",v1)


// Read/write property
const v2 = o.prw;
assertEquals("p-prw",v2)
o.prw = "bbb"
assertEquals("bbb",o.prw)


// Read/write property, invalid setter
const v3 = o.prw2;
assertEquals("p-prw2",v3)
try {
  o.prw2 = "ccc"
  fail();
} catch(e) {}
assertEquals("p-prw2",v3)


// Getter that returns void
const vv = o.voidProp
assertEquals(null,vv)


// Getter that has a parameter
const vp = o.paramProp
assertEquals(null,vp)
