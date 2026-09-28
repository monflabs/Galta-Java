function testScope() {
  if (true) {
    var myVar = 10;
    function myFunction() {
      return "Hello, world!";
    }
  }

  assertEquals(10,myVar);
  assertEquals("Hello, world!",myFunction());
}

testScope();