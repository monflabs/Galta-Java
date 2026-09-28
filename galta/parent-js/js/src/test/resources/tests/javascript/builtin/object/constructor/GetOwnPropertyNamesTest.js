const o = { a: 1, b: 2}
assertEquals( ["a", "b"], Object.getOwnPropertyNames(o).sort() )


const oMethods = Object.getOwnPropertyNames(Object);
console.log(oMethods)
assertTrue( oMethods.length>=10 ) // Many...
assertTrue( oMethods.indexOf("setPrototypeOf")>=0 ) // One, random
