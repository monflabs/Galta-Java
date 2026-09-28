// Create an instance of ArrayBuffer
let buffer = new ArrayBuffer(16);

// Test initial state
assertEquals(16, buffer.byteLength);

// Test slicing buffer
let sliced1 = buffer.slice(0);
assertEquals(16, sliced1.byteLength);

let sliced2 = buffer.slice(4, 12);
assertEquals(8, sliced2.byteLength);

let sliced3 = buffer.slice(8, 8);
assertEquals(0, sliced3.byteLength);

let sliced4 = buffer.slice(20);
assertEquals(0, sliced4.byteLength);

// Test creating an ArrayBuffer with different sizes
let smallBuffer = new ArrayBuffer(1);
assertEquals(1, smallBuffer.byteLength);

let largeBuffer = new ArrayBuffer(1024);
assertEquals(1024, largeBuffer.byteLength);

// Test exception handling for invalid buffer creation
assertThrows(RangeError, () => new ArrayBuffer(-1));
assertThrows(RangeError, () => new ArrayBuffer(2 ** 53)); // Exceeds max allowed size

// Test whether ArrayBuffer instances are separate
let buffer1 = new ArrayBuffer(8);
let buffer2 = buffer1.slice(0, 8);
assertEquals(8, buffer2.byteLength);
assertFalse(buffer1 === buffer2);





assertEquals(typeof ArrayBuffer, 'function');
assertEquals(Object.prototype.toString.call(ArrayBuffer), '[object Function]');

(function testConstructor() {
  const buf = new ArrayBuffer(8);
  assertEquals(buf instanceof ArrayBuffer, true);
  assertEquals(buf.byteLength, 8);
  assertEquals(typeof buf.byteLength, 'number');
})();

// Invalid constructor usage
(function testInvalidLengths() {
  assertThrows(RangeError, () => new ArrayBuffer(-1));
  assertEquals(0, (new ArrayBuffer(Number.NaN)).byteLength);
  assertEquals(0, (new ArrayBuffer('abc')).byteLength);
  assertEquals(0, (new ArrayBuffer({})).byteLength);
  assertEquals(0, (new ArrayBuffer()).byteLength);
})();

// Slice method
(function testSlice() {
  const buf = new ArrayBuffer(16);
  const slice = buf.slice(4, 12);
  assertEquals(slice instanceof ArrayBuffer, true);
  assertEquals(slice.byteLength, 8);

  const emptySlice = buf.slice(10, 5); // end < start
  assertEquals(emptySlice.byteLength, 0);
})();

// Slice with invalid arguments
(function testSliceErrors() {
  const buf = new ArrayBuffer(8);

  assertEquals(8, buf.slice('invalid').byteLength);
  assertEquals(0, buf.slice(0, 'bad').byteLength);
})();

// Property descriptors
(function testPropertyDescriptors() {
  const desc = Object.getOwnPropertyDescriptor(ArrayBuffer.prototype, 'byteLength');
  assertNotNull(desc);
  assertEquals(typeof desc.get, 'function');
  assertEquals(desc.set, undefined);
  assertEquals(desc.enumerable, false);
  assertEquals(desc.configurable, true);
})();

// Prototype and constructor
(function testPrototypeAndConstructor() {
  const buf = new ArrayBuffer(4);
  assertSame(Object.getPrototypeOf(buf), ArrayBuffer.prototype);
  assertSame(buf.constructor, ArrayBuffer);
  assertTrue(ArrayBuffer.prototype.hasOwnProperty('slice'));
})();

// Detaching behavior (simulated or engine-specific)
(function testDetachBehavior() {
  const buf = new ArrayBuffer(8);
  const view = new Uint8Array(buf);
  view[0] = 123;
  assertEquals(view[0], 123);

  // In some environments (e.g. postMessage with transfer), buffers can be detached.
  // Runtime-specific: skip actual detaching unless supported.
  // If detach API is available (e.g., %ArrayBufferDetach%), use it here.
})();

// Structured clone works (can be transferred)
(function testStructuredClone() {
  if (typeof structuredClone === 'function') {
    const buf = new ArrayBuffer(16);
    const clone = structuredClone(buf);
    assertEquals(clone instanceof ArrayBuffer, true);
    assertEquals(clone.byteLength, 16);
    assertFalse(clone === buf); // different instances
  }
})();

// TypedArray views
(function testTypedArrayViews() {
  const buf = new ArrayBuffer(16);
  const u32 = new Uint32Array(buf);
  assertEquals(u32.length, 4);
  u32[1] = 42;
  assertEquals(u32[1], 42);
})();

// Meta-properties
(function testObjectMeta() {
  const buf = new ArrayBuffer(4);
  assertTrue(Object.isExtensible(buf));
  assertFalse(Object.isSealed(buf));
  assertFalse(Object.isFrozen(buf));
})();

// Prototype symbols and toStringTag
(function testPrototypeProperties() {
  assertEquals(typeof ArrayBuffer.prototype.slice, 'function');
  assertEquals(ArrayBuffer.prototype[Symbol.toStringTag], 'ArrayBuffer');
  assertEquals(Object.prototype.toString.call(new ArrayBuffer(1)), '[object ArrayBuffer]');
})();

// Symbol.species is a getter-only accessor returning `this`.
(function testSpecies() {
  assertEquals(ArrayBuffer, ArrayBuffer[Symbol.species]);
  class MyBuffer extends ArrayBuffer {}
  assertEquals(MyBuffer, MyBuffer[Symbol.species]);
})();

// slice() uses the species constructor when present.
(function testSliceSpecies() {
  const buf = new ArrayBuffer(8);
  let called = false;
  buf.constructor = { [Symbol.species]: function(len) { called = true; return new ArrayBuffer(len); } };
  const s = buf.slice(0, 4);
  assertTrue(called);
  assertEquals(s.byteLength, 4);
})();

// Resizable buffers: maxByteLength option, resizable/maxByteLength getters, resize().
(function testResizable() {
  const fixed = new ArrayBuffer(4);
  assertEquals(fixed.resizable, false);
  assertEquals(fixed.maxByteLength, 4);
  assertThrows(TypeError, () => fixed.resize(4));

  const resizable = new ArrayBuffer(4, { maxByteLength: 8 });
  assertEquals(resizable.resizable, true);
  assertEquals(resizable.maxByteLength, 8);
  resizable.resize(6);
  assertEquals(resizable.byteLength, 6);
  resizable.resize(2); // resize() can shrink, unlike SharedArrayBuffer.grow()
  assertEquals(resizable.byteLength, 2);
  assertThrows(RangeError, () => resizable.resize(9));
  assertThrows(RangeError, () => resizable.resize(-1));

  assertThrows(RangeError, () => new ArrayBuffer(4, { maxByteLength: 2 }));
})();
