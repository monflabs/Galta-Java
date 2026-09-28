(function test___lookupSetter___ownProperty() {
  var o = {};
  function setX(v) { this._x = v; }

  o.__defineSetter__("x", setX);

  assertEqualsStrict(setX, o.__lookupSetter__("x"));
})();

(function test___lookupSetter___missingProperty() {
  var o = {};
  assertUndefined(o.__lookupSetter__("x"));
})();

(function test___lookupSetter___ignoresDataProperty() {
  var o = {};
  o.x = 10;

  assertUndefined(o.__lookupSetter__("x"));
})();

(function test___lookupSetter___prototypeLookup() {
  var proto = {};
  function setX(v) { this._x = v; }

  proto.__defineSetter__("x", setX);

  var o = Object.create(proto);

  // Should find setter on prototype
  assertEqualsStrict(setX, o.__lookupSetter__("x"));

  // And assignment should call it with receiver = o
  o.x = 99;
  assertEquals(99, o._x);
})();

(function test___lookupSetter___ownOverridesPrototype() {
  var proto = {};
  function protoSet(v) { this._x = v + 1; }
  function ownSet(v) { this._x = v + 10; }

  proto.__defineSetter__("x", protoSet);

  var o = Object.create(proto);
  o.__defineSetter__("x", ownSet);

  // Own setter should be returned, not prototype's
  assertEqualsStrict(ownSet, o.__lookupSetter__("x"));

  o.x = 1;
  assertEquals(11, o._x);
})();

(function test___lookupSetter___getterOnly() {
  var o = {};
  o.__defineGetter__("x", function () { return 1; });

  // Getter-only accessor has no setter
  assertUndefined(o.__lookupSetter__("x"));
})();

(function test___lookupSetter___nonStringPropertyKey() {
  var o = {};
  var sym = Symbol("x");

  function setSym(v) { this._sym = v; }
  o.__defineSetter__(sym, setSym);

  // __lookupSetter__ accepts property keys, including symbols
  assertEqualsStrict(setSym, o.__lookupSetter__(sym));

  o[sym] = 123;
  assertEquals(123, o._sym);
})();

(function test___lookupSetter___primitiveReceiver() {
  function setX(v) { /* can't persist on primitive, but lookup should work */ }

  Object.prototype.__defineSetter__("x", setX);

  // __lookupSetter__ should ToObject() the receiver
  assertEqualsStrict(setX, (42).__lookupSetter__("x"));
})();