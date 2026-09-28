// Helper: returns a BigInt from a numeric literal
// (This is just to keep code clean if needed.)
function bi(n) {
    return BigInt(n);
}

//
// 1. Constructor Tests and Basic Properties
//
function testBigInt64ArrayConstructor() {
	let arr0 = new BigInt64Array();
	assertEquals(0, arr0.length);
	assertEquals('[object BigInt64Array]', Object.prototype.toString.call(arr0));
	
    // A. Construct with length
    let arr1 = new BigInt64Array(5);
    assertEquals(5, arr1.length);
    // All elements should be 0n by default
    for (let i = 0; i < arr1.length; i++) {
        assertEquals(0n, arr1[i]);
    }

    // Check BYTES_PER_ELEMENT
    assertEquals(8, BigInt64Array.BYTES_PER_ELEMENT);
	assertEquals(8, BigInt64Array.prototype.BYTES_PER_ELEMENT);
	assertEquals(8, arr0.BYTES_PER_ELEMENT);

    // B. Construct from array-like of BigInts
    let arr2 = new BigInt64Array([10n, -20n, 30n]);
	assertEquals(3, arr2.length);
    assertEquals(10n, arr2[0]);
    assertEquals(-20n, arr2[1]);
    assertEquals(30n, arr2[2]);


    // C. Construct from another BigInt64Array
    let arr3 = new BigInt64Array(arr2);
    assertEquals(3, arr3.length);
    assertEquals(10n, arr3[0]);
    assertEquals(-20n, arr3[1]);
    assertEquals(30n, arr3[2]);
    // Changing arr2 should not affect arr3
    arr2[0] = 999n;
    assertEquals(10n, arr3[0]);

    // D. Construct from ArrayBuffer
    let buffer = new ArrayBuffer(16);
    let arr4 = new BigInt64Array(buffer);
    assertEquals(2, arr4.length); // 16 bytes / 8 bytes per element = 2
    assertSame(buffer, arr4.buffer);
    assertEquals(0, arr4.byteOffset);
    assertEquals(16, arr4.byteLength);

    // E. Construct from ArrayBuffer with offset and length
    //    Each element is 8 bytes.
    //    So offset=8, length=1 means we get 1 element starting at byte 8.
    let arr5 = new BigInt64Array(buffer, 8, 1);
    assertEquals(1, arr5.length);
    assertSame(buffer, arr5.buffer);
    assertEquals(8, arr5.byteOffset);
    assertEquals(8, arr5.byteLength);

    // F. Invalid constructor arguments
    // Negative length
    assertThrows(RangeError, () => new BigInt64Array(-1));
    // Offset out of range
    assertThrows(RangeError, () => new BigInt64Array(buffer, 17));
    // Offset + length out of range
    assertThrows(RangeError, () => new BigInt64Array(buffer, 0, 3)); // 3*8=24 bytes > buffer size 16
    // Non-integer length
	assertEquals(2, new BigInt64Array(2.5).length);
	assertEquals(2, new BigInt64Array("2").length);
	assertEquals(0, new BigInt64Array(null).length);
	assertEquals(0, new BigInt64Array(undefined).length);

    // G. Construct from array-like with non-BigInt values
    // By spec, a numeric value (like 42) cannot be automatically converted to BigInt
    // in a BigInt64Array constructor. This should throw a TypeError.
    assertThrows(TypeError, () => new BigInt64Array([42]));
}

//
// 2. Basic Array-Like Operations (index assignment, set, subarray, slice, etc.)
//
function testBigInt64ArrayBasicMethods() {
    // A. Basic index assignment
    let arr = new BigInt64Array(3);
    arr[0] = 123n;
    arr[1] = -456n;
    arr[2] = 2n ** 63n; // 2^63 -> wraps to -9223372036854775808n
    // Two’s complement mod 2^64:
    //  2^63 => MIN_SIGNED_64 = -9223372036854775808n
    assertEquals(123n, arr[0]);
    assertEquals(-456n, arr[1]);
    assertEquals(-9223372036854775808n, arr[2]);

    // B. set (from array-like of BigInt)
    let arr2 = new BigInt64Array(4);
    arr2.set([10n, -20n, 30n, 2n**63n]);
    assertEquals(10n, arr2[0]);
    assertEquals(-20n, arr2[1]);
    assertEquals(30n, arr2[2]);
    // 2^63 => -9223372036854775808n
    assertEquals(-9223372036854775808n, arr2[3]);

    // Attempt set with a numeric array => should throw TypeError on each invalid element
    let arr3 = new BigInt64Array(2);
    assertThrows(TypeError, () => arr3.set([42, 99]));

    // C. subarray (references the same buffer, does not copy)
    let arr4 = new BigInt64Array([10n, 20n, -30n, 40n]);
    let sub = arr4.subarray(1, 3); // elements [20n, -30n]
    assertEquals(2, sub.length);
    assertEquals(20n, sub[0]);
    // changes in sub reflect in arr4
    sub[1] = 999n;
    assertEquals(999n, arr4[2]);

    // D. slice (creates a copy if implemented)
    if (typeof arr4.slice === "function") {
        let sliceResult = arr4.slice(0, 2);
        assertEquals(2, sliceResult.length);
        assertEquals(10n, sliceResult[0]);
        // changes in sliceResult do not affect the original
        sliceResult[0] = 555n;
        assertEquals(10n, arr4[0]);
    } else {
        // If slice is not implemented, skip.
    }
}

//
// 3. Higher-Order Array Methods (map, filter, reduce, etc.)
//   BigInt64Array provides similar methods to normal typed arrays, 
//   except the callback always receives and returns BigInt values.
//
function testBigInt64ArrayArrayMethods() {
    let arr = new BigInt64Array([5n, 10n, -15n, 20n]);

    // A. forEach
    let sum = 0n;
    arr.forEach(val => sum += val);
    // sum = 5 + 10 + (-15) + 20 = 20n
    assertEquals(20n, sum);

    // B. map
    // map returns a new BigInt64Array
    let mapped = arr.map(x => x * 2n);
    assertEquals(arr.length, mapped.length);
    // [10n, 20n, -30n, 40n]
    assertEquals(10n, mapped[0]);
    assertEquals(20n, mapped[1]);
    assertEquals(-30n, mapped[2]);
    assertEquals(40n, mapped[3]);

    // Original array should be unchanged
    assertEquals(5n, arr[0]);

    // C. filter
    let filtered = arr.filter(x => x > 0n);
    // Should contain [5n, 10n, 20n]
    assertEquals(3, filtered.length);
    assertEquals(5n, filtered[0]);
    assertEquals(10n, filtered[1]);
    assertEquals(20n, filtered[2]);

    // D. reduce
    let product = arr.reduce((acc, val) => acc * val, 1n);
    // (1n * 5n) * 10n * -15n * 20n = -15000n
    assertEquals(-15000n, product);

    // E. some, every
    assertTrue(arr.some(x => x < 0n));
    assertFalse(arr.some(x => x === 999n));
    assertFalse(arr.every(x => x > 0n));
    assertTrue(arr.every(x => (x % 5n) === 0n));

    // F. find, findIndex
    let found = arr.find(x => x < 0n);
    assertEquals(-15n, found);
    let foundIndex = arr.findIndex(x => x < 0n);
    assertEquals(2, foundIndex);

    // G. reverse (in-place)
    let reversed = arr.reverse();
    assertSame(arr, reversed); // reversed is the same object
    // Original was [5n, 10n, -15n, 20n]
    // After reverse: [20n, -15n, 10n, 5n]
    assertEquals(20n, arr[0]);
    assertEquals(-15n, arr[1]);
    assertEquals(10n, arr[2]);
    assertEquals(5n, arr[3]);
}

//
// 4. Other Array Methods (copyWithin, fill, indexOf, includes, sort, etc.)
//
function testBigInt64ArrayOtherMethods() {
    let arr = new BigInt64Array([1n, 2n, 3n, 4n, 5n]);

    // A. copyWithin(target, start, end)
    arr.copyWithin(1, 2, 4);  // copy [3n,4n] to indices [1,2]
    // arr -> [1n, 3n, 4n, 4n, 5n]
    assertEquals(1n, arr[0]);
    assertEquals(3n, arr[1]);
    assertEquals(4n, arr[2]);
    assertEquals(4n, arr[3]);
    assertEquals(5n, arr[4]);

    // B. fill
    let arr2 = new BigInt64Array(5);
    arr2.fill(-100n, 1, 4);
    // arr2 -> [0n, -100n, -100n, -100n, 0n]
    assertEquals(0n, arr2[0]);
    assertEquals(-100n, arr2[1]);
    assertEquals(-100n, arr2[2]);
    assertEquals(-100n, arr2[3]);
    assertEquals(0n, arr2[4]);

    // C. indexOf, lastIndexOf
    let arr3 = new BigInt64Array([10n, -20n, 30n, -20n, 10n]);
    assertEquals(0, arr3.indexOf(10n));
    assertEquals(1, arr3.indexOf(-20n));
    assertEquals(-1, arr3.indexOf(99n));
    assertEquals(4, arr3.lastIndexOf(10n));
    assertEquals(3, arr3.lastIndexOf(-20n));
    // not found
    assertEquals(-1, arr3.lastIndexOf(999n));

    // D. includes
    assertTrue(arr3.includes(30n));
    assertFalse(arr3.includes(999n));
    // fromIndex
    assertTrue(arr3.includes(10n, 2));
	assertFalse(arr3.includes(30n, 3));

    // E. sort (in-place, numeric ascending for BigInts)
    let arr4 = new BigInt64Array([100n, 2n, -99n, 10n]);
    arr4.sort();
    // => [-99n, 2n, 10n, 100n]
    assertEquals(-99n, arr4[0]);
    assertEquals(2n, arr4[1]);
    assertEquals(10n, arr4[2]);
    assertEquals(100n, arr4[3]);
}

//
// 5. Iteration Protocols & Conversion
//
function testBigInt64ArrayIterationAndConversion() {
    let arr = new BigInt64Array([11n, 22n, -33n]);

    // A. entries()
    let entries = arr.entries();
    let e1 = entries.next();
    assertFalse(e1.done);
    // e1.value should be [index, element]
    assertEquals(0, e1.value[0]);
    assertEquals(11n, e1.value[1]);

    // B. keys()
    let keysIter = arr.keys();
    let k1 = keysIter.next();
    assertEquals(0, k1.value);
    assertFalse(k1.done);

    // C. values() and Symbol.iterator
    let valuesIter = arr.values();
    let v1 = valuesIter.next();
    assertEquals(11n, v1.value);
    let iter = arr[Symbol.iterator]();  // same as values()
    let i1 = iter.next();
    assertEquals(11n, i1.value);

    // D. toString / join
    // For typed arrays, toString often behaves like Array.prototype.toString
    let str = arr.toString();
    assertEquals("11,22,-33", str);
    let joined = arr.join("/");
    assertEquals("11/22/-33", joined);
}

//
// 6. Overflow, Wrapping, and Other Edge Cases
//
function testBigInt64ArrayEdgeCases() {
    // A. Overflow / wrapping checks
    let arr = new BigInt64Array(2);

    // Storing 2^63 yields minimum int64
    arr[0] = 2n ** 63n; // => -9223372036854775808n
    assertEquals(-9223372036854775808n, arr[0]);

    // Storing -2^63 - 1 => 2^63 - 1 wrap-around
    // -2^63 - 1 => 2^63 - 1 in 64-bit mod. So let's see:
    arr[1] = -(2n ** 63n) - 1n; 
    // -2^63 => -9223372036854775808n, minus 1 => 
    // two's complement mod => 9223372036854775807n
    assertEquals(9223372036854775807n, arr[1]);

    // B. set() with partial invalid elements
    // If any element is not a BigInt, it should throw a TypeError immediately
    let arr2 = new BigInt64Array(3);
    assertThrows(TypeError, () => arr2.set([1n, 2, 3n])); // '2' is not a BigInt

    // C. Negative index access
    // typedArray[-1] is just undefined in JS (not an error):
    assertEquals(undefined, arr[-1]);

	// D. Attempt to create from an empty array like
	assertEquals(0, new BigInt64Array({ a: 1n, b: 2n }).length );

	// E. Attempt typed array with detached buffer (engine-specific):
	let buffer = new ArrayBuffer(16);
	buffer.transfer();
	assertThrows(TypeError, () => new BigInt64Array(buffer));
}

//
// 7. Run all tests in a single function
//
function runBigInt64ArrayTests() {
    testBigInt64ArrayConstructor();
    testBigInt64ArrayBasicMethods();
    testBigInt64ArrayArrayMethods();
    testBigInt64ArrayOtherMethods();
    testBigInt64ArrayIterationAndConversion();
    testBigInt64ArrayEdgeCases();
}

// (Optional) Execute immediately:
runBigInt64ArrayTests();
