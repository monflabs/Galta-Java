//
// 1. Constructor Tests and Basic Properties
//
function testFloat64ArrayConstructor() {
	let arr0 = new Float64Array();
	assertEquals(0, arr0.length);
	assertEquals('[object Float64Array]', Object.prototype.toString.call(arr0));

	    // A. Construct with length
    let arr1 = new Float64Array(5);
    assertEquals(5, arr1.length);
    // All elements should be 0 by default
    for (let i = 0; i < arr1.length; i++) {
        assertEquals(0, arr1[i]);
    }

    // BYTES_PER_ELEMENT should be 8 for 64-bit floats
    assertEquals(8, Float64Array.BYTES_PER_ELEMENT);
	assertEquals(8, Float64Array.prototype.BYTES_PER_ELEMENT);
	assertEquals(8, arr0.BYTES_PER_ELEMENT);

    // B. Construct from array-like
    let arr2 = new Float64Array([1.25, -2.5, 1000.125]);
    assertEquals(3, arr2.length);

	 // Float64 stores these values with double precision, so it should be quite accurate
    assertEquals(1.25, arr2[0]);
    assertEquals(-2.5, arr2[1]);
    assertEquals(1000.125, arr2[2]);

    // C. Construct from another typed array
    let arr3 = new Float64Array(arr2);
    assertEquals(arr2.length, arr3.length);
    // Changing arr2 does not affect arr3
    arr2[0] = 9.75;
    assertEquals(1.25, arr3[0]); // arr3 is unchanged

    // D. Construct from ArrayBuffer
    //  16 bytes => can hold 2 Float64 elements
    let buffer = new ArrayBuffer(16);
    let arr4 = new Float64Array(buffer);
    assertEquals(2, arr4.length);
    assertSame(buffer, arr4.buffer);
    assertEquals(0, arr4.byteOffset);
    assertEquals(16, arr4.byteLength);

    // E. Construct with offset and length
    //    e.g., offset = 8, length = 1 => 1 float64 starting at byte 8
    let arr5 = new Float64Array(buffer, 8, 1);
    assertEquals(1, arr5.length);
    assertSame(buffer, arr5.buffer);
    assertEquals(8, arr5.byteOffset);
    assertEquals(8, arr5.byteLength);

    // F. Invalid constructor arguments
    // Negative length
    assertThrows(RangeError, () => new Float64Array(-1));
    // Offset out of range
    assertThrows(RangeError, () => new Float64Array(buffer, 17));
    // Offset + length out of range
    assertThrows(RangeError, () => new Float64Array(buffer, 0, 3)); // 3*8=24 > 16
	// Non-integer length
	assertEquals(2, new Float64Array(2.5).length);
	assertEquals(2, new Float64Array("2").length);
	assertEquals(0, new Float64Array(null).length);
	assertEquals(0, new Float64Array(undefined).length);
}

//
// 2. Special Float64 Behavior (Precision, Infinity, NaN, etc.)
//
function testFloat64ArraySpecialValues() {
    let arr = new Float64Array(6);

    // A. Large value => Infinity if beyond ~1.7976931348623157e308
    arr[0] = 1e400; // definitely > 1.79e308
    assertEquals(Infinity, arr[0]);

    // B. Very small value => might become 0 if underflow (below ~5e-324)
    arr[1] = 1e-330; 
    // double-precision min normal is ~2.2250738585072014e-308, and subnormal can go as low as ~5e-324
    // 1e-330 is smaller than that, so it likely becomes 0
    assertEquals(0, arr[1]);

    // C. Negative zero
    arr[2] = -0.0;
    assertEquals(0, arr[2]);
    // Optionally check 1 / arr[2] === -Infinity if negative zero is distinguished

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
function testFloat64ArrayBasicMethods() {
    let arr = new Float64Array([0.5, 1.0, 2.0]);

    // A. set (from array-like)
    arr.set([9.25, -8.75], 1);
    assertEquals(9.25, arr[1]);
    assertEquals(-8.75, arr[2]);

    // B. set (overlapping)
    let arr2 = new Float64Array([1.0, 2.0, 3.0, 4.0]);
    arr2.set(arr2.subarray(1, 3), 0); 
    // copy [2.0,3.0] to arr2[0..1]
    // => arr2 => [2.0, 3.0, 3.0, 4.0]
    assertEquals(2.0, arr2[0]);
    assertEquals(3.0, arr2[1]);
    assertEquals(3.0, arr2[2]);
    assertEquals(4.0, arr2[3]);

    // C. subarray (shares the same buffer)
    let arr3 = new Float64Array([10, 20, 30, 40]);
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
// 4. Higher-Order Array Methods (map, filter, reduce, etc.)
//
function testFloat64ArrayArrayMethods() {
    let arr = new Float64Array([0.5, 1.5, -2.0, 100]);

    // A. forEach
    let sum = 0;
    arr.forEach(val => sum += val);
    // => 0.5 + 1.5 + (-2.0) + 100 = 100
    assertEquals(100, sum);

    // B. map => returns a new Float64Array
    let mapped = arr.map(x => x * 2);
    // => [1.0, 3.0, -4.0, 200.0]
    assertEquals(4, mapped.length);
    assertEquals(1.0, mapped[0]);
    assertEquals(3.0, mapped[1]);
    assertEquals(-4.0, mapped[2]);
    assertEquals(200.0, mapped[3]);

    // Original array unchanged
    assertEquals(0.5, arr[0]);

    // C. filter
    let filtered = arr.filter(x => x > 0);
    // => [0.5, 1.5, 100]
    assertEquals(3, filtered.length);
    assertEquals(0.5, filtered[0]);
    assertEquals(1.5, filtered[1]);
    assertEquals(100, filtered[2]);

    // D. reduce
    let product = arr.reduce((acc, val) => acc * val, 1.0);
    // => 1.0 * 0.5 * 1.5 * -2.0 * 100 = -150
    assertEquals(-150, product);

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
    assertEquals(100, arr[0]);
    assertEquals(-2.0, arr[1]);
    assertEquals(1.5, arr[2]);
    assertEquals(0.5, arr[3]);
}

//
// 5. Other Array Methods (copyWithin, fill, indexOf, includes, sort, etc.)
//
function testFloat64ArrayOtherMethods() {
    let arr = new Float64Array([1.0, 2.0, 3.0, 4.0, 5.0]);

    // A. copyWithin
    arr.copyWithin(1, 2, 4); 
    // => [1.0, 3.0, 4.0, 4.0, 5.0]
    assertEquals(1.0, arr[0]);
    assertEquals(3.0, arr[1]);
    assertEquals(4.0, arr[2]);
    assertEquals(4.0, arr[3]);
    assertEquals(5.0, arr[4]);

    // B. fill
    let arr2 = new Float64Array(5);
    arr2.fill(9.25, 1, 4);
    // => [0, 9.25, 9.25, 9.25, 0]
    assertEquals(0, arr2[0]);
    assertEquals(9.25, arr2[1]);
    assertEquals(9.25, arr2[2]);
    assertEquals(9.25, arr2[3]);
    assertEquals(0, arr2[4]);

    // C. indexOf, lastIndexOf
    let arr3 = new Float64Array([10, 20, 30, 20, 10]);
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
    let arr4 = new Float64Array([100, -2, 1e200, 10]);
    arr4.sort();
    // => [-2, 10, 100, 1e200] (assuming no overflow for 1e200 in float64)
    assertEquals(-2, arr4[0]);
    assertEquals(10, arr4[1]);
    assertEquals(100, arr4[2]);
    // 1e200 is a valid float64 value, though it’s quite large
    assertEquals(1e200, arr4[3]);
}

//
// 6. Iteration Protocols & Conversion
//
function testFloat64ArrayIterationAndConversion() {
    let arr = new Float64Array([1.1, 2.2, 3.3]);

    // A. entries()
    let entries = arr.entries();
    let e1 = entries.next();
    assertFalse(e1.done);
    // e1.value is [index, element]
    assertEquals(0, e1.value[0]);
    assertEquals(1.1, e1.value[1]);

    // B. keys()
    let keysIter = arr.keys();
    let k1 = keysIter.next();
    assertEquals(0, k1.value);
    assertFalse(k1.done);

    // C. values() and Symbol.iterator
    let valuesIter = arr.values();
    let v1 = valuesIter.next();
    assertEquals(1.1, v1.value);

    let iter = arr[Symbol.iterator](); // same as values()
    let i1 = iter.next();
    assertEquals(1.1, i1.value);

    // D. toString / join
    let str = arr.toString();
    // e.g. "1.1,2.2,3.3"
    assertTrue(typeof str === "string");
    assertTrue(str.indexOf(",") >= 0);

    let joined = arr.join("-");
    assertTrue(typeof joined === "string");
    assertTrue(joined.indexOf("-") >= 0);
}

//
// 7. Error & Edge Cases
//
function testFloat64ArrayErrorCases() {
    let arr = new Float64Array(5);

    // A. set: out of range offset
    assertThrows(RangeError, () => arr.set([1.0, 2.0, 3.0], 4));
    // Only room for 1 element at index 4

    // B. Negative index => just undefined, not an error
    assertEquals(undefined, arr[-1]);

    // C. subarray with out-of-bounds (clamps, no error)
    let sub = arr.subarray(3, 10);
    assertEquals(2, sub.length); // index 3..4 inclusive

	// D. Attempt to create from an empty array like
	assertEquals(0, new Float64Array({ a: 1, b: 2 }).length );

	// E. Attempt typed array with detached buffer (engine-specific):
	let buffer = new ArrayBuffer(16);
	buffer.transfer();
	assertThrows(TypeError, () => new Float64Array(buffer));
}

//
// 8. Run all tests
//
function runFloat64ArrayTests() {
    testFloat64ArrayConstructor();
    testFloat64ArraySpecialValues();
    testFloat64ArrayBasicMethods();
    testFloat64ArrayArrayMethods();
    testFloat64ArrayOtherMethods();
    testFloat64ArrayIterationAndConversion();
    testFloat64ArrayErrorCases();
}

// (Optional) Execute all tests now:
runFloat64ArrayTests();
