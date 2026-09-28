const obj = {};
Object.defineProperties(obj, {
  property1: {
    writable: true,
  },
  property2: {
    value: true,
    writable: true,
  },
  property3: {
    value: "Hello",
    writable: false,
  },
});


assertEquals(undefined, obj.property1)
assertEquals(true, obj.property2)
assertEquals("Hello", obj.property3)

obj.property2 = false;
assertEquals(false, obj.property2)

assertThrows( () => {obj.property3 = "world";} );
assertEquals("Hello", obj.property3)
