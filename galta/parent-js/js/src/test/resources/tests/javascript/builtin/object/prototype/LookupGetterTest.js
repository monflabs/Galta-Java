(function test___lookupGetter___ownProperty() {
  var o = {};
  function getX() { return 42; }

  o.__defineGetter__("x", getX);

  assertEqualsStrict(getX, o.__lookupGetter__("x"));
})();

(function test___lookupGetter___missingProperty() {
  var o = {};

  assertUndefined(o.__lookupGetter__("x"));
})();

(function test___lookupGetter___ignoresDataProperty() {
  var o = {};
  o.x = 10;

  assertUndefined(o.__lookupGetter__("x"));
})();

(function test___lookupGetter___prototypeLookup() {
  var proto = {};
  function getX() { return this._x; }

  proto.__defineGetter__("x", getX);

  var o = Object.create(proto);
  o._x = 99;

  // Should find getter on prototype
  assertEqualsStrict(getX, o.__lookupGetter__("x"));
  assertEquals(99, o.x);
})();

(function test___lookupGetter___ownOverridesPrototype() {
  var proto = {};
  function protoGet() { return 1; }
  function ownGet() { return 2; }

  proto.__defineGetter__("x", protoGet);

  var o = Object.create(proto);
  o.__defineGetter__("x", ownGet);

  // Own getter should be returned, not prototype's
  assertEqualsStrict(ownGet, o.__lookupGetter__("x"));
  assertEquals(2, o.x);
})();

(function test___lookupGetter___setterOnly() {
  var o = {};
  o.__defineSetter__("x", function (v) { this._x = v; });

  // Setter-only accessor has no getter
  assertUndefined(o.__lookupGetter__("x"));
})();

(function test___lookupGetter___nonStringPropertyKey() {
  var o = {};
  var sym = Symbol("x");

  function getSym() { return 123; }
  o.__defineGetter__(sym, getSym);

  // __lookupGetter__ accepts property keys, including symbols
  assertEqualsStrict(getSym, o.__lookupGetter__(sym));
})();

(function test___lookupGetter___primitiveReceiver() {
  function getX() { return 1; }

  Object.prototype.__defineGetter__("x", getX);

  // __lookupGetter__ should ToObject() the receiver
  assertEqualsStrict(getX, (42).__lookupGetter__("x"));
})();