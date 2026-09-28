(function test___defineSetter___basic() {
  var o = {};
  var calls = 0;
  var last;

  o.__defineSetter__("x", function (v) {
    calls++;
    last = v;
  });

  o.x = 42;
  assertEquals(1, calls);
  assertEquals(42, last);

  o["x"] = 99;
  assertEquals(2, calls);
  assertEquals(99, last);
})();

(function test___defineSetter___thisBinding() {
  var o = { base: 10 };
  var seenThis;

  o.__defineSetter__("x", function (v) {
    // `this` should be the receiver of the assignment
    seenThis = this;
    this.base = v;
  });

  o.x = 123;
  assertEquals(123, o.base);
  assertEqualsStrict(o, seenThis);

  var child = Object.create(o);
  child.x = 456;
  // Setter is inherited, but `this` should be child (receiver)
  assertEquals(456, child.base);
  assertEqualsStrict(child, seenThis);
})();

(function test___defineSetter___descriptorDefaults() {
  var o = {};
  o.__defineSetter__("x", function (v) { /*noop*/ });

  var d = Object.getOwnPropertyDescriptor(o, "x");
  assertTrue(!!d);
  assertTrue(typeof d.set === "function");
  assertUndefined(d.get);

  // Common legacy behavior: enumerable/configurable default to true.
  // This test will reveal if your engine differs.
  assertEqualsStrict(true, d.enumerable);
  assertEqualsStrict(true, d.configurable);
})();

(function test___defineSetter___writesDontCreateDataProperty() {
  var o = {};
  var captured;

  o.__defineSetter__("x", function (v) { captured = v; });

  o.x = 7;

  // Still an accessor, not a data property with "value".
  var d = Object.getOwnPropertyDescriptor(o, "x");
  assertTrue(!!d);
  assertTrue(typeof d.set === "function");
  assertUndefined(d.value);

  assertEquals(7, captured);
})();

(function test___defineSetter___overwritesDataProperty() {
  var o = {};
  o.x = 100;

  o.__defineSetter__("x", function (v) { this._x = v + 1; });

  o.x = 10;
  assertEquals(11, o._x);

  var d = Object.getOwnPropertyDescriptor(o, "x");
  assertTrue(!!d);
  assertTrue(typeof d.set === "function");
  assertUndefined(d.value);
})();

(function test___defineSetter___redefineSetter() {
  var o = {};
  o.__defineSetter__("x", function (v) { this._x = v; });
  o.x = 1;
  assertEquals(1, o._x);

  o.__defineSetter__("x", function (v) { this._x = v * 2; });
  o.x = 2;
  assertEquals(4, o._x);
})();

(function test___defineSetter___nonExtensibleThrows() {
  var o = {};
  Object.preventExtensions(o);

  var threw = false;
  try {
    o.__defineSetter__("x", function (v) {});
  } catch (e) {
    threw = true;
    // Usually TypeError (legacy corner, but commonly enforced).
    // assertTrue(e instanceof TypeError);
  }
  assertTrue(threw);
})();

(function test___defineSetter___invalidSetterThrows() {
  var o = {};
  var threw = false;
  try {
    o.__defineSetter__("x", 123); // setter must be callable
  } catch (e) {
    threw = true;
    // Usually TypeError.
  }
  assertTrue(threw);
})();