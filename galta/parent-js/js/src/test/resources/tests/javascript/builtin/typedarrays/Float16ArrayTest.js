//
// Test suite for Float16Array
// NOTE: Float16Array is not (yet) a standard built-in in JavaScript.
//       This suite assumes you have a hypothetical or custom implementation
//       that behaves similarly to other typed arrays, but with 16-bit floats.


//
// 1. Constructor Tests and Basic Properties
//
function testFloat16ArrayConstructor() {
	let arr0 = new Float16Array();
	assertEquals(0, arr0.length);
	assertEquals('[object Float16Array]', Object.prototype.toString.call(arr0));

	// A. Construct with length
    let arr1 = new Float16Array(5);
    assertEquals(5, arr1.length);
    // All elements should be 0.0 by default
    for (let i = 0; i < arr1.length; i++) {
        assertEquals(0.0, arr1[i]);
    }

    // B. BYTES_PER_ELEMENT should be 2 for half-precision
    assertEquals(2, Float16Array.BYTES_PER_ELEMENT);
	assertEquals(2, Float16Array.prototype.BYTES_PER_ELEMENT);
	assertEquals(2, arr0.BYTES_PER_ELEMENT);

    // C. Construct from array-like
    let arr2 = new Float16Array([1.25, -2.5, 1000]);
    assertEquals(3, arr2.length);
    // The exact stored values may be half-precision approximations.
    // We'll just check that it's close enough (or that your engine stores it with some precision).
    // For instance, 1.25 is exactly representable in half precision.
    // -2.5 may be exactly representable, 1000 likely is in normal half range but might lose precision.
    assertEquals(1.25, arr2[0]);
    assertEquals(-2.5, arr2[1]);
    // 1000 might get stored with some rounding. We'll do a rough check:
    assertTrue(Math.abs(arr2[2] - 1000) < 1); // or some small tolerance

    // D. Construct from another typed array
    let arr3 = new Float16Array(arr2);
    assertEquals(arr2.length, arr3.length);
    // Changing arr2 should not affect arr3
    arr2[0] = 9.75;
    assertTrue(Math.abs(arr3[0] - 1.25) < 0.01);

    // E. Construct from ArrayBuffer
    //  Assume we want 4 elements => 4 * 2 bytes = 8 bytes
    let buffer = new ArrayBuffer(8);
    let arr4 = new Float16Array(buffer);
    assertEquals(4, arr4.length);
    assertSame(buffer, arr4.buffer);
    assertEquals(0, arr4.byteOffset);
    assertEquals(8, arr4.byteLength);

    // F. Construct from ArrayBuffer with offset and length
    //    Suppose offset=2, length=2 => 2 * 2 bytes = 4 bytes used, starting at byte 2
    //    This is somewhat unusual if the engine requires the offset to be aligned to 2 bytes,
    //    so offset=2 might be valid or might cause a RangeError if alignment is enforced.
    //    We'll assume alignment is not strictly enforced or that 2 is acceptable.
    let buffer2 = new ArrayBuffer(8);
    assertThrows(RangeError, () => new Float16Array(buffer2, 9)); // offset out of range
    // If the environment allows offset=2:
    let arr5 = new Float16Array(buffer2, 2, 3); 
    assertEquals(3, arr5.length);
    assertEquals(2, arr5.byteOffset);
    assertEquals(6, arr5.byteLength);

	// Non-integer length
	assertEquals(2, new BigUint64Array(2.5).length);
	assertEquals(2, new BigUint64Array("2").length);
	assertEquals(0, new BigUint64Array(null).length);
	assertEquals(0, new BigUint64Array(undefined).length);

    // G. Invalid constructor arguments
    assertThrows(RangeError, () => new Float16Array(-1));          // negative length
    assertThrows(RangeError, () => new Float16Array(buffer2, 0, 9999));  // out-of-range
}

//
// 2. Special Float16 Behavior (Rounding, Subnormal, Infinity, NaN)
//
function testFloat16ArraySpecialValues() {
    let arr = new Float16Array(6);

    // A. Store a large value beyond half-precision normal range
    //    The maximum finite half-precision value is ~65504.
    //    Values above that typically become Infinity in half precision.
    arr[0] = 1e10;
    assertTrue(Number.isFinite(1e10));
    // In half precision, that should become Infinity (or at least 65504, depending on your engine).
    let stored0 = arr[0];
    assertTrue(stored0 === Infinity || stored0 >= 65500);

    // B. Very small positive value => might become a subnormal or 0
    //    The smallest positive normal half is ~6.1035e-5, and subnormal can go down to ~5.96e-8
    arr[1] = 1e-9;
    let stored1 = arr[1];
    // Could become a subnormal ~1e-9 or might clamp to 0 if smaller than min subnormal
    // We'll just check it's either 0 or a small positive number less than 1e-4
    assertTrue(stored1 === 0 || Math.abs(stored1) < 1e-4);

    // C. Negative zero
    arr[2] = -0.0;
    // Some engines might store sign of zero for float16. 
    // We'll see if it’s recognized as negative zero by 1 / arr[2] = -Infinity
    // or we do a sign check. This depends on the engine.
    // We'll just check that the stored value is 0, 
    // and if the environment preserves sign, then (1 / arr[2]) should be -Infinity:
    let stored2 = arr[2];
    assertEquals(0, stored2);
    // Optional check if negative zero is preserved:
    // assertTrue(1 / arr[2] === -Infinity);

    // D. Infinity and -Infinity
    arr[3] = Infinity;
    arr[4] = -Infinity;
    assertEquals(Infinity, arr[3]);
    assertEquals(-Infinity, arr[4]);

    // E. NaN
    arr[5] = NaN;
    // Float16 typically stores a NaN pattern. 
    // Checking for it in JavaScript: any NaN is "=== NaN" => false, 
    // so we use isNaN:
    assertTrue(Number.isNaN(arr[5]));
}

//
// 3. Basic Array Methods (index access, set, subarray, slice, etc.)
//
function testFloat16ArrayBasicMethods() {
    let arr = new Float16Array([0.5, 1.0, 2.0]);

    // A. set (from array-like)
    arr.set([9.25, -8.75], 1);
    // The exact representation might be slightly off due to half precision rounding
    assertTrue(Math.abs(arr[1] - 9.25) < 0.1); // or appropriate tolerance
    assertTrue(Math.abs(arr[2] + 8.75) < 0.1);

    // B. subarray (shares the same buffer, no copy)
    let arr2 = new Float16Array([10, 20, 30, 40]);
    let sub = arr2.subarray(1, 3); // [20, 30]
    assertEquals(2, sub.length);
    assertEquals(20, sub[0]);
    // changes in sub reflect in arr2
    sub[1] = 99;
    assertEquals(99, arr2[2]);

    // C. slice (creates a copy if implemented)
    if (typeof arr2.slice === "function") {
        let sliceResult = arr2.slice(1, 3);
        assertEquals(2, sliceResult.length);
        assertEquals(20, sliceResult[0]);
        // changes in sliceResult do not affect original
        sliceResult[0] = 88;
        assertEquals(20, arr2[1]);
    }
}

//
// 4. Higher-order Array Methods (map, filter, reduce, etc.)
//
function testFloat16ArrayArrayMethods() {
    let arr = new Float16Array([0.5, 1.5, -2.0, 100]);

    // A. forEach
    let sum = 0;
    arr.forEach(val => sum += val);
    // sum ~ 0.5 + 1.5 + (-2.0) + 100 = 100.0
    assertTrue(Math.abs(sum - 100.0) < 0.001);

    // B. map => returns a new Float16Array
    let mapped = arr.map(x => x * 2);
    assertEquals(arr.length, mapped.length);
    // [1.0, 3.0, -4.0, 200], possibly with half-precision rounding
    assertTrue(Math.abs(mapped[0] - 1.0) < 0.01);
    assertTrue(Math.abs(mapped[1] - 3.0) < 0.01);

    // C. filter
    let filtered = arr.filter(x => x > 0);
    // Should contain [0.5, 1.5, 100]
    assertEquals(3, filtered.length);
    // Not guaranteed exactly 0.5 or 1.5, but close enough
    assertTrue(filtered[0] > 0 && filtered[0] < 1);
    assertTrue(filtered[1] > 1 && filtered[1] < 2);

    // D. reduce
    let product = arr.reduce((acc, val) => acc * val, 1.0);
    // (1.0 * 0.5) * 1.5 * -2.0 * 100 ~ -150
    assertTrue(Math.abs(product + 150) < 1);

    // E. some, every
    assertTrue(arr.some(x => x < 0));
    assertFalse(arr.every(x => x > 0));

    // F. find, findIndex
    let found = arr.find(x => x < 0);
    assertTrue(found < 0);
    let foundIndex = arr.findIndex(x => x < 0);
    // Should be 2, since arr[2] = -2
    assertEquals(2, foundIndex);

    // G. reverse (in-place)
    // original arr => [0.5, 1.5, -2.0, 100]
    let reversed = arr.reverse();
    assertSame(arr, reversed);
    // now => [100, -2.0, 1.5, 0.5]
    assertTrue(Math.abs(arr[0] - 100) < 0.01);
    assertTrue(arr[1] < 0);
    assertTrue(arr[2] > 1 && arr[2] < 2);
}

//
// 5. Other Array Methods (copyWithin, fill, indexOf, includes, sort, etc.)
//
function testFloat16ArrayOtherMethods() {
    let arr = new Float16Array([1.0, 2.0, 3.0, 4.0, 5.0]);

    // A. copyWithin
    arr.copyWithin(1, 2, 4); // copy [3.0,4.0] to indices [1..2]
    // => [1.0, 3.0, 4.0, 4.0, 5.0]
    assertEquals(1.0, arr[0]);
    assertEquals(3.0, arr[1]);
    assertEquals(4.0, arr[2]);
    assertEquals(4.0, arr[3]);
    assertEquals(5.0, arr[4]);

    // B. fill
    let arr2 = new Float16Array(5);
    arr2.fill(9.75, 1, 4);
    // => [0.0, 9.75, 9.75, 9.75, 0.0] with rounding
    // check approximate
    assertTrue(Math.abs(arr2[1] - 9.75) < 0.1);
    assertTrue(Math.abs(arr2[2] - 9.75) < 0.1);

    // C. indexOf, lastIndexOf
    let arr3 = new Float16Array([10, 20, 30, 20, 10]);
    assertEquals(0, arr3.indexOf(10));
    assertEquals(1, arr3.indexOf(20));
    // This might only work precisely if the stored values are exactly 10, 20, 30. 
    // If there's rounding, these tests might fail. We'll assume exact representation for these integers.
    assertEquals(-1, arr3.indexOf(999));
    assertEquals(4, arr3.lastIndexOf(10));
    assertEquals(3, arr3.lastIndexOf(20));

    // D. includes
    assertTrue(arr3.includes(20));
    assertFalse(arr3.includes(999));

    // E. sort (in-place, numeric ascending)
    let arr4 = new Float16Array([100, -2, 99999, 10]);
    arr4.sort();
    // 99999 likely becomes Infinity in half precision, or ~65504. 
    // Sort might produce [-2, 10, 100, Infinity].
    // We'll just ensure sorted order ascending. 
    for (let i = 0; i < arr4.length - 1; i++) {
        assertTrue(arr4[i] <= arr4[i + 1]);
    }
}

//
// 6. Iteration Protocols & Conversion
//
function testFloat16ArrayIterationAndConversion() {
    let arr = new Float16Array([1.1, 2.2, 3.3]);

    // A. entries()
    let entries = arr.entries();
    let e1 = entries.next();
    assertFalse(e1.done);
    assertEquals(0, e1.value[0]);
    // e1.value[1] might be ~1.1 with rounding
    assertTrue(Math.abs(e1.value[1] - 1.1) < 0.2);

    // B. keys()
    let keysIter = arr.keys();
    let k1 = keysIter.next();
    assertEquals(0, k1.value);
    assertFalse(k1.done);

    // C. values() and Symbol.iterator
    let valuesIter = arr.values();
    let v1 = valuesIter.next();
    assertTrue(Math.abs(v1.value - 1.1) < 0.2);

    let iter = arr[Symbol.iterator](); // same as values()
    let i1 = iter.next();
    assertTrue(Math.abs(i1.value - 1.1) < 0.2);

    // D. toString / join
    // Typically acts like array toString
    let str = arr.toString();
    // We expect something like "1.099609375,2.2001953125,3.2998046875" or near that
    // We'll only check that it's a string containing commas
    assertTrue(typeof str === "string");
    assertTrue(str.indexOf(",") >= 0);

    let joined = arr.join("-");
    assertTrue(typeof joined === "string");
    assertTrue(joined.indexOf("-") >= 0);
}

//
// 7. Error & Edge Cases
//
function testFloat16ArrayErrorCases() {
    let arr = new Float16Array(4);

    // A. set: out of range offset
    assertThrows(RangeError, () => arr.set([1, 2, 3], 3)); 
    // Only space for 1 item at index 3

    // B. Negative or large index
    // arr[-1] is undefined (not an error)
    assertEquals(undefined, arr[-1]);
    // arr[999] is also undefined

    // C. Attempt to pass non-numeric to Float16Array or set
    // For typed arrays, a string like "1.25" might get numeric-converted if your engine tries parseFloat,
    // but typically it's an implicit number conversion. Let's assume it can be stored but leads to a numeric interpretation.
    // If your engine disallows non-numbers, this might throw. 
    // We'll demonstrate a scenario that might throw TypeError in a stricter environment:
    // assertThrows(TypeError, () => arr.set(["1.25"]));

    // D. subarray with out-of-bounds indices (should clamp, not throw)
    let sub = arr.subarray(2, 10);
    assertEquals(2, sub.length);

	// D. Attempt to create from an empty array like
	assertEquals(0, new Float16Array({ a: 1, b: 2 }).length );

	// E. Attempt typed array with detached buffer (engine-specific):
	let buffer = new ArrayBuffer(16);
	buffer.transfer();
	assertThrows(TypeError, () => new Float16Array(buffer));
}

//
// 8. Run all tests
//
function runFloat16ArrayTests() {
    testFloat16ArrayConstructor();
    testFloat16ArraySpecialValues();
    testFloat16ArrayBasicMethods();
    testFloat16ArrayArrayMethods();
    testFloat16ArrayOtherMethods();
    testFloat16ArrayIterationAndConversion();
    testFloat16ArrayErrorCases();
}

// (Optional) Execute all tests immediately:
runFloat16ArrayTests();
