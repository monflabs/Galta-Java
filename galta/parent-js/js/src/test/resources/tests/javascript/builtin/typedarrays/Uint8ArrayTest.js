//
// 1. Constructor Tests and Basic Properties
//
function testUint8ArrayConstructor() {
	let arr0 = new Uint8Array();
	assertEquals(0, arr0.length);
	assertEquals('[object Uint8Array]', Object.prototype.toString.call(arr0));
	
    // A. Construct with length
    let arr1 = new Uint8Array(5);
    assertEquals(5, arr1.length);
    // All elements should be 0 by default
    for (let i = 0; i < arr1.length; i++) {
        assertEquals(0, arr1[i]);
    }

    // Check BYTES_PER_ELEMENT
    assertEquals(1, Uint8Array.BYTES_PER_ELEMENT);
	assertEquals(1, Uint8Array.prototype.BYTES_PER_ELEMENT);
	assertEquals(1, arr0.BYTES_PER_ELEMENT);

    // B. Construct from array-like
    let arr2 = new Uint8Array([10, 20, 30]);
	assertEquals(1, arr2.BYTES_PER_ELEMENT);
    assertEquals(3, arr2.length);
    assertEquals(10, arr2[0]);
    assertEquals(20, arr2[1]);
    assertEquals(30, arr2[2]);

    // C. Construct from another typed array
    let arr3 = new Uint8Array(arr2);
    assertEquals(3, arr3.length);
    assertEquals(10, arr3[0]);
    assertEquals(20, arr3[1]);
    assertEquals(30, arr3[2]);
    // Changing arr2 should not affect arr3
    arr2[0] = 99;
    assertEquals(10, arr3[0]);

    // D. Construct from ArrayBuffer
    let buffer = new ArrayBuffer(8);
    let arr4 = new Uint8Array(buffer);
    assertEquals(8, arr4.length);
    assertSame(buffer, arr4.buffer);
    assertEquals(0, arr4.byteOffset);
    assertEquals(8, arr4.byteLength);

    // E. Construct from ArrayBuffer with offset and length
    let arr5 = new Uint8Array(buffer, 2, 4);
    assertEquals(4, arr5.length);
    assertSame(buffer, arr5.buffer);
    assertEquals(2, arr5.byteOffset);
    assertEquals(4, arr5.byteLength);

    // F. Invalid constructor arguments
    // Negative length
    assertThrows(RangeError, () => new Uint8Array(-1));
    // Offset out of range
    assertThrows(RangeError, () => new Uint8Array(buffer, 9));
    // Offset + length out of range
    assertThrows(RangeError, () => new Uint8Array(buffer, 2, 10));
	// Non-integer length
	assertEquals(2, new Uint8Array(2.5).length);
	assertEquals(2, new Uint8Array("2").length);
	assertEquals(0, new Uint8Array(null).length);
	assertEquals(0, new Uint8Array(undefined).length);
}

//
// 2. Basic Array Methods (index access, set, subarray, etc.)
//
function testUint8ArrayBasicMethods() {
    let arr = new Uint8Array([0, 1, 2, 3, 4, 5]);

    // A. set (from array-like)
    arr.set([9, 8], 2);
    assertEquals(9, arr[2]);
    assertEquals(8, arr[3]);
    // Check that other elements are unchanged
    assertEquals(0, arr[0]);
    assertEquals(1, arr[1]);

    // B. set (from itself, overlapping)
    let arr2 = new Uint8Array([1, 2, 3, 4]);
    arr2.set(arr2.subarray(1, 3), 0); // move [2,3] into arr2[0..1]
    assertEquals(2, arr2[0]);
    assertEquals(3, arr2[1]);
    assertEquals(3, arr2[2]); // the 3 remains at index 2
    assertEquals(4, arr2[3]);

    // C. subarray (this does not copy; it references same buffer)
    let arr3 = new Uint8Array([10, 20, 30, 40, 50]);
    let sub = arr3.subarray(1, 4);
    assertEquals(3, sub.length);
    assertEquals(20, sub[0]);
    // changes in sub reflect in arr3
    sub[1] = 99;
    assertEquals(99, arr3[2]);

    // D. slice (this creates a copy in modern engines)
    //   Not all engines have typed-array-specific slice, but many do now.
    if (typeof arr3.slice === "function") {
        let sliceResult = arr3.slice(1, 3);
        assertEquals(2, sliceResult.length);
        assertEquals(20, sliceResult[0]);
        // changes in sliceResult do not affect the original
        sliceResult[0] = 88;
        assertEquals(20, arr3[1]);
    } else {
        // If slice is not implemented, just skip
    }
}

//
// 3. Higher-order Array Methods (map, filter, reduce, etc.)
//
function testUint8ArrayArrayMethods() {
    let arr = new Uint8Array([5, 10, 15, 20, 25]);

    // A. forEach
    let sum = 0;
    arr.forEach(val => sum += val);
    assertEquals(75, sum);

    // B. map
    // NOTE: map returns a new Uint8Array
    let mapped = arr.map(x => x / 5);
    assertEquals(5, mapped.length);
    assertEquals(1, mapped[0]);
    assertEquals(2, mapped[1]);
    assertEquals(3, mapped[2]);
    // Original array should be unchanged
    assertEquals(5, arr[0]);

    // C. filter
    let filtered = arr.filter(x => x > 10);
    assertEquals(3, filtered.length);
    assertEquals(15, filtered[0]);
    assertEquals(20, filtered[1]);
    assertEquals(25, filtered[2]);

    // D. reduce
    let product = arr.reduce((acc, val) => acc * val, 1);
    assertEquals(5 * 10 * 15 * 20 * 25, product);

    // E. some, every
    assertTrue(arr.some(x => x === 10));
    assertFalse(arr.some(x => x === 99));
    assertTrue(arr.every(x => x % 5 === 0));
    assertFalse(arr.every(x => x < 15));

    // F. find, findIndex
    let found = arr.find(x => x > 10);
    assertEquals(15, found);
    let foundIndex = arr.findIndex(x => x > 10);
    assertEquals(2, foundIndex);

    // G. reverse
    // reverse modifies the array in place
    let reversed = arr.reverse();
    assertSame(arr, reversed); // reversed is the same object
    assertEquals(25, arr[0]);
    assertEquals(20, arr[1]);
    assertEquals(15, arr[2]);
    assertEquals(10, arr[3]);
    assertEquals(5, arr[4]);
}

//
// 4. Other Array Methods (copyWithin, fill, indexOf, includes, etc.)
//
function testUint8ArrayOtherMethods() {
    let arr = new Uint8Array([1, 2, 3, 4, 5]);

    // A. copyWithin
    // copyWithin(target, start, end)
    arr.copyWithin(1, 2, 4);  // copy [3,4] to indices [1,2]
    assertEquals(1, arr[0]);
    assertEquals(3, arr[1]);
    assertEquals(4, arr[2]);
    assertEquals(4, arr[3]);
    assertEquals(5, arr[4]);

    // B. fill
    let arr2 = new Uint8Array([0, 0, 0, 0, 0]);
    arr2.fill(9, 1, 4);
    // arr2 -> [0, 9, 9, 9, 0]
    assertEquals(0, arr2[0]);
    assertEquals(9, arr2[1]);
    assertEquals(9, arr2[2]);
    assertEquals(9, arr2[3]);
    assertEquals(0, arr2[4]);

    // C. indexOf, lastIndexOf
    let arr3 = new Uint8Array([10, 20, 30, 20, 10]);
    assertEquals(0, arr3.indexOf(10));
    assertEquals(1, arr3.indexOf(20));
    assertEquals(-1, arr3.indexOf(99));
    assertEquals(4, arr3.lastIndexOf(10));
    assertEquals(3, arr3.lastIndexOf(20));
    // not found
    assertEquals(-1, arr3.lastIndexOf(99));

    // D. includes
    assertTrue(arr3.includes(30));
    assertFalse(arr3.includes(99));
    // fromIndex
    assertTrue(arr3.includes(10, 2));
	assertFalse(arr3.includes(30, 3));

    // E. sort (in-place)
    let arr4 = new Uint8Array([100, 2, 99, 10]);
    // The default sort for typed arrays is ascending, numeric
    arr4.sort();
    // [2, 10, 99, 100]
    assertEquals(2, arr4[0]);
    assertEquals(10, arr4[1]);
    assertEquals(99, arr4[2]);
    assertEquals(100, arr4[3]);
}

//
// 5. Iteration Protocols & Conversion
//
function testUint8ArrayIterationAndConversion() {
    let arr = new Uint8Array([11, 22, 33]);

    // A. entries()
    let entries = arr.entries();
    let e1 = entries.next();
    assertFalse(e1.done);
    // e1.value should be [index, element]
    assertEquals(0, e1.value[0]);
    assertEquals(11, e1.value[1]);

    // B. keys()
    let keysIter = arr.keys();
    let k1 = keysIter.next();
    assertEquals(0, k1.value);
    assertFalse(k1.done);

    // C. values() and Symbol.iterator
    let valuesIter = arr.values();
    let v1 = valuesIter.next();
    assertEquals(11, v1.value);
    let iter = arr[Symbol.iterator]();  // same as values()
    let i1 = iter.next();
    assertEquals(11, i1.value);

    // D. toString / join
    // For typed arrays, toString often defaults to Array.prototype.toString-like behavior
    let str = arr.toString();
    assertEquals("11,22,33", str);
    let joined = arr.join("-");
    assertEquals("11-22-33", joined);
}

//
// 6. Error Cases & Edge Cases
//
function testUint8ArrayErrorCases() {
    let arr = new Uint8Array(5);

    // A. set: out of range offset
    assertThrows(RangeError, () => arr.set([1, 2, 3], 4)); // only room for 2 elements at offset 4

    // B. subarray with invalid arguments (though subarray typically clamps)
    // Typically subarray won't throw for out-of-bounds; it just clamps indices.
    // We'll just ensure it doesn't throw:
    let sub = arr.subarray(2, 10);
    assertEquals(3, sub.length); // from index 2..5

    // C. Negative or out-of-range typed array indexing is not allowed
    // Access like arr[-1] is just undefined in JS. Not an error, but let's confirm.
    assertEquals(undefined, arr[-1]);

	// D. Attempt to create from an empty array like
	assertEquals(0, new Uint8Array({ a: 1, b: 2 }).length );

	// E. Attempt typed array with detached buffer (engine-specific):
	let buffer = new ArrayBuffer(16);
	buffer.transfer();
	assertThrows(TypeError, () => new Uint8Array(buffer));

    // F. Sorting with a non-function compare argument.
    let arr2 = new Uint8Array([2, 1]);
    assertThrows(TypeError, () => arr2.sort("not a function") );

	// G. Assigning a value whose magnitude exceeds Integer.MAX_VALUE must
	// wrap modulo 2^8, not saturate (see Int8ArrayTest.js for the root cause).
	arr[0] = 0xF7F7F7F7;
	assertEquals(0xF7, arr[0]);
}

//
// 7. Run all tests in a single function
//
function runUint8ArrayTests() {
    testUint8ArrayConstructor();
    testUint8ArrayBasicMethods();
    testUint8ArrayArrayMethods();
    testUint8ArrayOtherMethods();
    testUint8ArrayIterationAndConversion();
    testUint8ArrayErrorCases();
}

// (Optional) Execute immediately:
runUint8ArrayTests();
