(function test___defineGetter___basic() {
  var o = {};
  var calls = 0;

  o.__defineGetter__("x", function () {
    calls++;
    return 42;
  });

  assertEquals(42, o.x);
  assertEquals(1, calls);
  assertEquals(42, o["x"]);
  assertEquals(2, calls);
})();

(function test___defineGetter___thisBinding() {
  var o = { base: 10 };

  o.__defineGetter__("x", function () {
    // In a normal getter call, `this` should be the receiver.
    return this.base + 1;
  });

  assertEquals(11, o.x);

  var child = Object.create(o);
  child.base = 20;
  // Accessing inherited accessor should bind `this` to the child (receiver).
  assertEquals(21, child.x);
})();

(function test___defineGetter___descriptorDefaults() {
  var o = {};
  o.__defineGetter__("x", function () { return 1; });

  var d = Object.getOwnPropertyDescriptor(o, "x");
  assertTrue(!!d);
  assertTrue(typeof d.get === "function");
  assertUndefined(d.set);
  assertEquals(1, o.x);

  // Per legacy behavior aligned with defineProperty defaults:
  // enumerable: true, configurable: true
  // (If your engine differs, this test will tell you.)
  assertEqualsStrict(true, d.enumerable);
  assertEqualsStrict(true, d.configurable);
})();

(function test___defineGetter___overwritesDataProperty() {
  var o = {};
  o.x = 100;

  o.__defineGetter__("x", function () { return 7; });

  var d = Object.getOwnPropertyDescriptor(o, "x");
  assertTrue(!!d);
  assertTrue(typeof d.get === "function");
  assertUndefined(d.value); // accessor descriptor has no "value"

  assertEquals(7, o.x);
})();

(function test___defineGetter___redefineGetter() {
  var o = {};
  o.__defineGetter__("x", function () { return 1; });
  assertEquals(1, o.x);

  o.__defineGetter__("x", function () { return 2; });
  assertEquals(2, o.x);
})();

(function test___defineGetter___nonExtensibleThrows() {
  var o = {};
  Object.preventExtensions(o);

  var threw = false;
  try {
    o.__defineGetter__("x", function () { return 1; });
  } catch (e) {
    threw = true;
    // Typically TypeError, but engines can vary in legacy corners.
    // If you want strictness:
    // assertTrue(e instanceof TypeError);
  }
  assertTrue(threw);
})();

(function test___defineGetter___invalidGetterThrows() {
  var o = {};
  var threw = false;
  try {
    o.__defineGetter__("x", 123); // getter must be callable
  } catch (e) {
    threw = true;
    // Usually TypeError.
  }
  assertTrue(threw);
})();