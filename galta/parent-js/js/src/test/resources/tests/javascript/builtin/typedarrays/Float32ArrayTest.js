//
// 1. Constructor Tests and Basic Properties
//
function testFloat32ArrayConstructor() {
	let arr0 = new Float32Array();
	assertEquals(0, arr0.length);
	assertEquals('[object Float32Array]', Object.prototype.toString.call(arr0));

	    // A. Construct with length
    let arr1 = new Float32Array(5);
    assertEquals(5, arr1.length);
    // All elements should be 0 by default
    for (let i = 0; i < arr1.length; i++) {
        assertEquals(0, arr1[i]);
    }

    // BYTES_PER_ELEMENT should be 4 for 32-bit floats
    assertEquals(4, Float32Array.BYTES_PER_ELEMENT);
	assertEquals(4, Float32Array.prototype.BYTES_PER_ELEMENT);
	assertEquals(4, arr0.BYTES_PER_ELEMENT);

    // B. Construct from array-like
    let arr2 = new Float32Array([1.25, -2.5, 1000.125]);
    assertEquals(3, arr2.length);
    // Float32 might store approximate values, but these should be close.
    assertTrue(Math.abs(arr2[0] - 1.25) < 0.0001);
    assertTrue(Math.abs(arr2[1] + 2.5) < 0.0001);
    // 1000.125 may lose some precision in float32, but it should be close.
    assertTrue(Math.abs(arr2[2] - 1000.125) < 0.01);

    // C. Construct from another typed array
    let arr3 = new Float32Array(arr2);
    assertEquals(arr2.length, arr3.length);
    // Changing arr2 does not affect arr3
    arr2[0] = 9.75;
    assertTrue(Math.abs(arr3[0] - 1.25) < 0.01);

    // D. Construct from ArrayBuffer
    //  8 bytes => can hold 2 float32 elements
    let buffer = new ArrayBuffer(8);
    let arr4 = new Float32Array(buffer);
    assertEquals(2, arr4.length);
    assertSame(buffer, arr4.buffer);
    assertEquals(0, arr4.byteOffset);
    assertEquals(8, arr4.byteLength);

    // E. Construct with offset and length
    //    e.g., offset = 4, length = 1 => 1 float32 starting at byte 4
    let arr5 = new Float32Array(buffer, 4, 1);
    assertEquals(1, arr5.length);
    assertSame(buffer, arr5.buffer);
    assertEquals(4, arr5.byteOffset);
    assertEquals(4, arr5.byteLength);

    // F. Invalid constructor arguments
    // Negative length
    assertThrows(RangeError, () => new Float32Array(-1));
    // Offset out of range
    assertThrows(RangeError, () => new Float32Array(buffer, 9));
    // Offset + length out of range
    assertThrows(RangeError, () => new Float32Array(buffer, 0, 3));

	// Non-integer length
	assertEquals(2, new Float32Array(2.5).length);
	assertEquals(2, new Float32Array("2").length);
	assertEquals(0, new Float32Array(null).length);
	assertEquals(0, new Float32Array(undefined).length);
}

//
// 2. Special Float32 Behavior (Precision, Infinity, NaN, etc.)
//
function testFloat32ArraySpecialValues() {
    let arr = new Float32Array(6);

    // A. Large value => Infinity
    arr[0] = 1e40;  // above float32 max finite ~3.402823e38
    assertEquals(Infinity, arr[0]);

    // B. Very small value => may become 0 if underflow
    arr[1] = 1e-50; // below float32 min normal ~1.175494e-38; might store as 0 or subnormal
    let stored1 = arr[1];
    assertTrue(stored1 === 0 || Math.abs(stored1) < 1e-38);

    // C. Negative zero
    arr[2] = -0.0;
    // Check that it's numerically 0:
    assertEquals(0, arr[2]);
    // If negative zero is preserved, 1 / arr[2] == -Infinity, but not all tests do that check.

    // D. Infinity and -Infinity
    arr[3] = Infinity;
    arr[4] = -Infinity;
    assertEquals(Infinity, arr[3]);
    assertEquals(-Infinity, arr[4]);

    // E. NaN
    arr[5] = NaN;
    assertTrue(Number.isNaN(arr[5]));
}

//
// 3. Basic Array Methods (index access, set, subarray, slice, etc.)
//
function testFloat32ArrayBasicMethods() {
    let arr = new Float32Array([0.5, 1.0, 2.0]);

    // A. set (from array-like)
    arr.set([9.25, -8.75], 1);
    // Float32 might round these, but they'll be close
    assertTrue(Math.abs(arr[1] - 9.25) < 0.01);
    assertTrue(Math.abs(arr[2] + 8.75) < 0.01);

    // B. set (overlapping)
    let arr2 = new Float32Array([1.0, 2.0, 3.0, 4.0]);
    arr2.set(arr2.subarray(1, 3), 0); // copy [2,3] to arr2[0..1]
    // => arr2 => [2, 3, 3, 4]
    assertEquals(2, arr2[0]);
    assertEquals(3, arr2[1]);
    assertEquals(3, arr2[2]);
    assertEquals(4, arr2[3]);

    // C. subarray (shares the same buffer)
    let arr3 = new Float32Array([10, 20, 30, 40]);
    let sub = arr3.subarray(1, 3); // [20, 30]
    assertEquals(2, sub.length);
    assertEquals(20, sub[0]);
    // changes in sub reflect in arr3
    sub[1] = 99;
    assertEquals(99, arr3[2]);

    // D. slice (creates a copy if implemented)
    if (typeof arr3.slice === "function") {
        let sliceResult = arr3.slice(1, 3);
        assertEquals(2, sliceResult.length);
        assertEquals(20, sliceResult[0]);
        // changes in sliceResult do not affect arr3
        sliceResult[0] = 88;
        assertEquals(20, arr3[1]);
    }
}

//
// 4. Higher-Order Array Methods (map, filter, reduce, forEach, some, every, find, findIndex, reverse)
//
function testFloat32ArrayArrayMethods() {
    let arr = new Float32Array([0.5, 1.5, -2.0, 100]);

    // A. forEach
    let sum = 0;
    arr.forEach(val => sum += val);
    // => 0.5 + 1.5 + (-2.0) + 100 = 100
    assertTrue(Math.abs(sum - 100) < 0.0001);

    // B. map => returns new Float32Array
    let mapped = arr.map(x => x * 2);
    // => [1.0, 3.0, -4.0, 200]
    assertTrue(Math.abs(mapped[0] - 1.0) < 0.001);
    assertTrue(Math.abs(mapped[1] - 3.0) < 0.001);

    // C. filter
    let filtered = arr.filter(x => x > 0);
    // => [0.5, 1.5, 100]
    assertEquals(3, filtered.length);
    assertTrue(filtered[0] > 0 && filtered[0] < 1);
    assertTrue(filtered[1] > 1 && filtered[1] < 2);

    // D. reduce
    let product = arr.reduce((acc, val) => acc * val, 1.0);
    // => 1.0 * 0.5 * 1.5 * -2.0 * 100 ~ -150
    assertTrue(Math.abs(product + 150) < 1);

    // E. some, every
    assertTrue(arr.some(x => x < 0));
    assertFalse(arr.every(x => x > 0));

    // F. find, findIndex
    let found = arr.find(x => x < 0);
    assertEquals(-2.0, found);
    let foundIndex = arr.findIndex(x => x < 0);
    assertEquals(2, foundIndex);

    // G. reverse (in-place)
    let reversed = arr.reverse();
    assertSame(arr, reversed);
    // original => [0.5, 1.5, -2.0, 100]
    // reversed => [100, -2.0, 1.5, 0.5]
    assertTrue(Math.abs(arr[0] - 100) < 0.001);
    assertEquals(-2.0, arr[1]);
    assertTrue(arr[2] > 1 && arr[2] < 2);
    assertTrue(arr[3] > 0 && arr[3] < 1);
}

//
// 5. Other Array Methods (copyWithin, fill, indexOf, includes, sort, etc.)
//
function testFloat32ArrayOtherMethods() {
    let arr = new Float32Array([1.0, 2.0, 3.0, 4.0, 5.0]);

    // A. copyWithin(target, start, end)
    arr.copyWithin(1, 2, 4); // copy [3.0, 4.0] to arr[1..2]
    // => [1.0, 3.0, 4.0, 4.0, 5.0]
    assertEquals(1.0, arr[0]);
    assertEquals(3.0, arr[1]);
    assertEquals(4.0, arr[2]);
    assertEquals(4.0, arr[3]);
    assertEquals(5.0, arr[4]);

    // B. fill
    let arr2 = new Float32Array(5);
    arr2.fill(9.25, 1, 4);
    // => [0, 9.25, 9.25, 9.25, 0]
    // may be slightly off in float32 representation
    assertTrue(Math.abs(arr2[1] - 9.25) < 0.01);
    assertTrue(Math.abs(arr2[2] - 9.25) < 0.01);

    // C. indexOf, lastIndexOf
    let arr3 = new Float32Array([10, 20, 30, 20, 10]);
    assertEquals(0, arr3.indexOf(10));
    assertEquals(1, arr3.indexOf(20));
    assertEquals(-1, arr3.indexOf(999));
    assertEquals(4, arr3.lastIndexOf(10));
    assertEquals(3, arr3.lastIndexOf(20));

    // D. includes
    assertTrue(arr3.includes(30));
    assertFalse(arr3.includes(999));
    // fromIndex
    assertTrue(arr3.includes(10, 2));
	assertFalse(arr3.includes(30, 3));

    // E. sort (in-place, numeric ascending)
    let arr4 = new Float32Array([100, -2, 1e38, 10]);
    arr4.sort();
    // [ -2, 10, 100, 1e38 ]
    // 1e38 is within float32 range (though near the upper limit).
    assertEquals(-2, arr4[0]);
    assertEquals(10, arr4[1]);
    assertEquals(100, arr4[2]);
    assertTrue(Math.abs(arr4[3] - 1e38) < 1e36);
}

//
// 6. Iteration Protocols & Conversion
//
function testFloat32ArrayIterationAndConversion() {
    let arr = new Float32Array([1.1, 2.2, 3.3]);

    // A. entries()
    let entries = arr.entries();
    let e1 = entries.next();
    assertFalse(e1.done);
    // e1.value is [index, element]
    assertEquals(0, e1.value[0]);
    // element might be slightly off
    assertTrue(Math.abs(e1.value[1] - 1.1) < 0.1);

    // B. keys()
    let keysIter = arr.keys();
    let k1 = keysIter.next();
    assertEquals(0, k1.value);
    assertFalse(k1.done);

    // C. values() and Symbol.iterator
    let valuesIter = arr.values();
    let v1 = valuesIter.next();
    assertTrue(Math.abs(v1.value - 1.1) < 0.1);

    let iter = arr[Symbol.iterator](); // same as values()
    let i1 = iter.next();
    assertTrue(Math.abs(i1.value - 1.1) < 0.1);

    // D. toString / join
    let str = arr.toString();
    // e.g. "1.1000000238418579,2.200000047683716,3.299999952316284"
    assertTrue(typeof str === "string");
    assertTrue(str.indexOf(",") >= 0);

    let joined = arr.join("-");
    assertTrue(typeof joined === "string");
    assertTrue(joined.indexOf("-") >= 0);
}

//
// 7. Error & Edge Cases
//
function testFloat32ArrayErrorCases() {
    let arr = new Float32Array(5);

    // A. set: out of range offset
    assertThrows(RangeError, () => arr.set([1.0, 2.0, 3.0], 4));
    // Only room for 1 element at index 4

    // B. Negative index => not an error, just undefined
    assertEquals(undefined, arr[-1]);

    // C. subarray with out-of-bounds (should clamp, not throw)
    let sub = arr.subarray(3, 10);
    assertEquals(2, sub.length); // from index 3..4

	// D. Attempt to create from an empty array like
	assertEquals(0, new Float32Array({ a: 1, b: 2 }).length );

	// E. Attempt typed array with detached buffer (engine-specific):
	let buffer = new ArrayBuffer(16);
	buffer.transfer();
	assertThrows(TypeError, () => new Float32Array(buffer));
}

//
// 8. Run all tests
//
function runFloat32ArrayTests() {
    testFloat32ArrayConstructor();
    testFloat32ArraySpecialValues();
    testFloat32ArrayBasicMethods();
    testFloat32ArrayArrayMethods();
    testFloat32ArrayOtherMethods();
    testFloat32ArrayIterationAndConversion();
    testFloat32ArrayErrorCases();
}

// (Optional) Execute all tests automatically:
runFloat32ArrayTests();
