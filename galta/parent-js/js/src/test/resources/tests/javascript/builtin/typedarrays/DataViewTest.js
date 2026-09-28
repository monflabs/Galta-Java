// Test 1: Basic constructor usage and properties
function testDataViewConstructor() {
    // 1. Construct a DataView with a new ArrayBuffer
    let buffer = new ArrayBuffer(16);
    let view = new DataView(buffer);

    // Check that the buffer is the same object
    assertSame(buffer, view.buffer);

    // Check byteOffset and byteLength
    assertEquals(0, view.byteOffset);
    assertEquals(16, view.byteLength);

    // 2. Construct a DataView with offset and length
    let view2 = new DataView(buffer, 4, 8);
    assertSame(buffer, view2.buffer);
    assertEquals(4, view2.byteOffset);
    assertEquals(8, view2.byteLength);

    // 3. Constructing DataView with offset but no length should go to end
    let view3 = new DataView(buffer, 8);
    assertEquals(8, view3.byteOffset);
    // remainder from 8 to 16 is 8 bytes
    assertEquals(8, view3.byteLength);

    // 4. Invalid constructor calls
    assertThrows(RangeError, () => new DataView(buffer, 17));        // offset out of range
    assertThrows(RangeError, () => new DataView(buffer, 10, 10));    // offset + length out of range
    assertThrows(TypeError,   () => new DataView(null));             // invalid buffer
    assertThrows(TypeError,   () => new DataView("not a buffer"));   // invalid buffer
}

// Test 2: Setting and getting integer values
function testDataViewIntegers() {
    let buffer = new ArrayBuffer(8);
    let view = new DataView(buffer);

    // setInt8 / getInt8
    view.setInt8(0, -128);
    assertEquals(-128, view.getInt8(0));

    // setUint8 / getUint8
    view.setUint8(1, 255);
    assertEquals(255, view.getUint8(1));

    // setInt16 / getInt16 (little endian)
    view.setInt16(2, -32768, true);
    assertEquals(-32768, view.getInt16(2, true));

    // setInt16 / getInt16 (big endian)
    view.setInt16(2, 12345, false);
    assertEquals(12345, view.getInt16(2, false));

    // setUint16 / getUint16 (little endian)
    view.setUint16(2, 65535, true);
    assertEquals(65535, view.getUint16(2, true));

    // setInt32 / getInt32 (big endian)
    view.setInt32(0, -12345678, false);
    assertEquals(-12345678, view.getInt32(0, false));

    // setUint32 / getUint32 (little endian)
    view.setUint32(0, 4294967295, true);
    assertEquals(4294967295, view.getUint32(0, true));
}

// Test 3: Setting and getting floating-point values
function testDataViewFloats() {
    let buffer = new ArrayBuffer(16);
    let view = new DataView(buffer);

    // setFloat32 / getFloat32
    view.setFloat32(0, 1.25, true);    // little endian
    assertEquals(1.25, view.getFloat32(0, true));

    view.setFloat32(0, -3.75, false);  // big endian
    // Testing exact floating point equality can be tricky in real setups,
    // but here we rely on 'assertEquals' with '==' for simplicity.
    assertEquals(-3.75, view.getFloat32(0, false));

    // setFloat64 / getFloat64
    view.setFloat64(0, 1234.5678, true);     // little endian
    assertEquals(1234.5678, view.getFloat64(0, true));

    view.setFloat64(0, -9876.54321, false);  // big endian
    assertEquals(-9876.54321, view.getFloat64(0, false));
}

// Test 4: Edge cases and errors
function testDataViewEdgeCases() {
    let buffer = new ArrayBuffer(4);
    let view = new DataView(buffer);

    // 1. Out-of-range set/get
    assertThrows(RangeError, () => view.getInt8(4));      // index out of range
    assertThrows(RangeError, () => view.setUint32(1, 1)); // tries to write beyond length

    // 2. Negative index (invalid)
    assertThrows(RangeError, () => view.getInt8(-1));

    // 3. Using a detached buffer or similar is engine-specific, but
    //    we can still test that operations throw if the buffer is out-of-range
    //    Typically you'd need an internal API to detach the buffer to confirm.
    //    We'll skip a "detach" test unless your engine provides one.

    // 4. Check that DataView does not expand buffer
    //    The following is just a conceptual check; the buffer won't expand.
    view.setInt8(0, 100);
    assertEquals(100, view.getInt8(0));
    assertEquals(4, view.byteLength);

    // 5. Attempt to create a DataView with offsets that align exactly at the boundary
    //    This should succeed (not an error).
    let view2 = new DataView(buffer, 4, 0);
    assertEquals(4, view2.byteOffset);
    assertEquals(0, view2.byteLength);
}

// Test: DataView also accepts a SharedArrayBuffer (not just ArrayBuffer)
function testDataViewOverSharedArrayBuffer() {
    let sab = new SharedArrayBuffer(8);
    let view = new DataView(sab);
    assertSame(sab, view.buffer);
    assertEquals(0, view.getUint8(0));
    view.setInt32(0, 12345);
    assertEquals(12345, view.getInt32(0));
}

// Regression: get/set must be relative to the DataView's own byteOffset, not
// the start of the underlying buffer.
function testDataViewNonZeroByteOffset() {
    let buffer = new ArrayBuffer(8);
    let view = new DataView(buffer, 4, 4);
    view.setInt8(0, 42);
    assertEquals(42, view.getInt8(0));
    assertEquals(0, new DataView(buffer, 0, 4).getInt8(0));
    assertEquals(42, new Int8Array(buffer)[4]);
}

// ToIndex conversions on the byteOffset argument: undefined defaults to 0,
// objects/strings/booleans coerce, negative or non-integral values throw
// RangeError before the value argument is ever touched.
function testDataViewToIndex() {
    let buffer = new ArrayBuffer(8);
    let view = new DataView(buffer);
    view.setInt8(2, 0);
    view.setInt8({valueOf: () => 2}, 7);
    assertEquals(7, view.getInt8(2));
    view.setInt8(0, 0);
    view.setInt8("0", 9);
    assertEquals(9, view.getInt8(0));

    assertThrows(RangeError, () => view.setInt8(-1, 0));
    assertThrows(RangeError, () => view.setInt8(-1.5, 0));
    assertThrows(RangeError, () => view.setInt8(Infinity, 0));

    // Index ToIndex conversion happens BEFORE the value's ToNumber - a
    // poisoned valueOf() on the value must never be reached for a bad index.
    let touched = false;
    let poisoned = {valueOf: () => { touched = true; return 0; }};
    assertThrows(RangeError, () => view.setInt8(-1, poisoned));
    assertEquals(false, touched);

    // Missing value argument: ToNumber(undefined) is NaN, not an error.
    view.setInt8(0, 7);
    view.setInt8(0);
    assertEquals(0, view.getInt8(0));
}

// Detached buffer: byteLength/byteOffset getters and every get/set method
// must throw TypeError (not silently return stale data).
function testDataViewDetachedBuffer() {
    let buffer = new ArrayBuffer(8);
    let view = new DataView(buffer, 0);
    buffer.transfer(0); // detaches the original buffer
    assertThrows(TypeError, () => view.byteLength);
    assertThrows(TypeError, () => view.byteOffset);
    assertThrows(TypeError, () => view.getInt8(0));
    assertThrows(TypeError, () => view.setInt8(0, 1));
}

// Resizable ArrayBuffer support: a length-tracking ("auto") DataView follows
// the buffer's current size; a fixed-length DataView throws once the buffer
// shrinks below its own end.
function testDataViewResizableBuffer() {
    let buffer = new ArrayBuffer(4, {maxByteLength: 8});

    let auto = new DataView(buffer, 1); // no explicit byteLength -> tracking
    assertEquals(3, auto.byteLength);
    buffer.resize(8);
    assertEquals(7, auto.byteLength);
    buffer.resize(1);
    // Still in bounds (byteOffset 1 <= current length 1), just a zero-length
    // view now - reading anything overruns that (empty) view: RangeError.
    assertEquals(0, auto.byteLength);
    assertThrows(RangeError, () => auto.getInt8(0));
    buffer.resize(0);
    // Now byteOffset(1) > current length(0): genuinely out of bounds.
    assertThrows(TypeError, () => auto.byteLength);
    assertThrows(TypeError, () => auto.getInt8(0));

    buffer.resize(4);
    let fixed = new DataView(buffer, 0, 4);
    assertEquals(4, fixed.byteLength);
    buffer.resize(2);
    assertThrows(TypeError, () => fixed.byteLength);
    assertThrows(TypeError, () => fixed.getInt8(0));
    buffer.resize(4);
    assertEquals(4, fixed.byteLength); // back in bounds again
}

// A float32 NaN read back must remain a genuine, self-unequal JS NaN (not a
// bare Java Float leaking past the engine's Double-based number model).
function testDataViewFloatNaN() {
    let buffer = new ArrayBuffer(4);
    let view = new DataView(buffer);
    view.setFloat32(0, NaN);
    let v = view.getFloat32(0);
    assertEquals(true, Number.isNaN(v));
    assertEquals(true, v !== v);
    assertEquals(false, v === v);
}

// Execute all tests
function runDataViewTests() {
    testDataViewConstructor();
    testDataViewIntegers();
    testDataViewFloats();
    testDataViewEdgeCases();
    testDataViewOverSharedArrayBuffer();
    testDataViewNonZeroByteOffset();
    testDataViewToIndex();
    testDataViewDetachedBuffer();
    testDataViewResizableBuffer();
    testDataViewFloatNaN();
}

// (Optional) Call runDataViewTests() automatically:
runDataViewTests();
