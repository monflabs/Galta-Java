// SharedArrayBuffer.test.js

// Test basic construction
assertEquals(typeof SharedArrayBuffer, 'function');
assertEquals(Object.prototype.toString.call(SharedArrayBuffer), '[object Function]');

(function testConstructor() {
  const sab = new SharedArrayBuffer(8);
  assertEquals(sab instanceof SharedArrayBuffer, true);
  assertEquals(sab.byteLength, 8);
  assertEquals(typeof sab.byteLength, 'number');
})();

// Test invalid construction
(function testInvalidLengths() {
	assertThrows(RangeError, () => new SharedArrayBuffer(-1));
	assertEquals(0, (new SharedArrayBuffer(Number.NaN)).byteLength);
	assertEquals(0, (new SharedArrayBuffer('abc')).byteLength);
	assertEquals(0, (new SharedArrayBuffer({})).byteLength);
	assertEquals(0, (new SharedArrayBuffer()).byteLength);
})();

// Test slicing
(function testSlice() {
  const sab = new SharedArrayBuffer(16);
  const slice = sab.slice(4, 12);
  assertEquals(slice instanceof SharedArrayBuffer, true);
  assertEquals(slice.byteLength, 8);

  const emptySlice = sab.slice(10, 5); // end < start
  assertEquals(emptySlice.byteLength, 0);
})();

// slice() coerces non-numeric start/end via ToNumber (NaN -> 0), per spec -
// it does not throw for a plain non-numeric string.
(function testSliceErrors() {
  const sab = new SharedArrayBuffer(8);

  assertEquals(sab.slice('invalid').byteLength, 8);
  assertEquals(sab.slice(0, 'bad').byteLength, 0);
})();

// slice() clamps an out-of-range end index instead of throwing
(function testSliceClampsEnd() {
  const sab = new SharedArrayBuffer(8);
  const s = sab.slice(1, 999);
  assertEquals(s.byteLength, 7);
})();

// slice() coerces start/end via ToNumber (objects with valueOf/toString)
(function testSliceNumberConversion() {
  const sab = new SharedArrayBuffer(8);
  const start = { valueOf() { return 2; } };
  const end = { valueOf() { return 6; } };
  const s = sab.slice(start, end);
  assertEquals(s.byteLength, 4);
})();

// slice() uses the species constructor when present
(function testSliceSpecies() {
  const sab = new SharedArrayBuffer(8);
  let called = false;
  sab.constructor = { [Symbol.species]: function(len) { called = true; return new SharedArrayBuffer(len); } };
  const s = sab.slice(0, 4);
  assertTrue(called);
  assertEquals(s.byteLength, 4);
})();

// growable buffers: maxByteLength option, growable/maxByteLength getters, grow()
(function testGrowable() {
  const fixed = new SharedArrayBuffer(4);
  assertEquals(fixed.growable, false);
  assertEquals(fixed.maxByteLength, 4);
  assertThrows(TypeError, () => fixed.grow(4));

  const growable = new SharedArrayBuffer(4, { maxByteLength: 8 });
  assertEquals(growable.growable, true);
  assertEquals(growable.maxByteLength, 8);
  growable.grow(6);
  assertEquals(growable.byteLength, 6);
  assertThrows(RangeError, () => growable.grow(9));
  assertThrows(RangeError, () => growable.grow(5));

  assertThrows(RangeError, () => new SharedArrayBuffer(4, { maxByteLength: 2 }));
})();

// Test property descriptors
(function testPropertyDescriptors() {
  const desc = Object.getOwnPropertyDescriptor(SharedArrayBuffer.prototype, 'byteLength');
  assertNotNull(desc);
  assertEquals(typeof desc.get, 'function');
  assertEquals(desc.set, undefined);
  assertEquals(desc.enumerable, false);
  assertEquals(desc.configurable, true);
})();

// Test prototype and constructor linkage
(function testPrototypeAndConstructor() {
  const sab = new SharedArrayBuffer(4);
  assertEquals(Object.getPrototypeOf(sab), SharedArrayBuffer.prototype);
  assertEquals(sab.constructor, SharedArrayBuffer);
  assertTrue(SharedArrayBuffer.prototype.hasOwnProperty('slice'));
})();

// Test SharedArrayBuffer is not detached
(function testDetachedState() {
  const sab = new SharedArrayBuffer(16);
  // SABs can't be detached like ArrayBuffers; just checking they still behave
  assertEquals(new Uint8Array(sab).length, 16);
})();

// Test SharedArrayBuffer cannot be transferred (structured clone)
(function testStructuredCloneThrows() {
  // In a compliant engine, structured cloning should throw for SABs
  // if transfer list is used or if cloning is disallowed
  if (typeof structuredClone === 'function') {
    const sab = new SharedArrayBuffer(8);
    assertThrows(DataCloneError, () => structuredClone(sab));
  }
})();

// Test shared usage with typed arrays
(function testTypedArrayViews() {
  const sab = new SharedArrayBuffer(16);
  const u8 = new Uint8Array(sab);
  assertEquals(u8.length, 16);
  u8[0] = 255;
  assertEquals(u8[0], 255);
})();

// Test SharedArrayBuffer is enumerable and extensible
(function testObjectMeta() {
  const sab = new SharedArrayBuffer(8);
  assertTrue(Object.isExtensible(sab));
  assertFalse(Object.isSealed(sab));
  assertFalse(Object.isFrozen(sab));
})();

// Test SharedArrayBuffer.prototype properties
(function testPrototypeProperties() {
  assertEquals(typeof SharedArrayBuffer.prototype.slice, 'function');
  assertEquals(SharedArrayBuffer.prototype[Symbol.toStringTag], 'SharedArrayBuffer');
  assertEquals(Object.prototype.toString.call(new SharedArrayBuffer(1)), '[object SharedArrayBuffer]');
})();
