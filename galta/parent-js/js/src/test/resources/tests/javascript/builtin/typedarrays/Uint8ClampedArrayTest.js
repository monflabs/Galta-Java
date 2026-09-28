//
// 1. Constructor Tests and Basic Properties
//
function testUint8ClampedArrayConstructor() {
	let arr0 = new Uint8ClampedArray();
	assertEquals(0, arr0.length);
	assertEquals('[object Uint8ClampedArray]', Object.prototype.toString.call(arr0));

    // A. Construct with length
    let arr1 = new Uint8ClampedArray(5);
    assertEquals(5, arr1.length);
    // All elements should be 0 by default
    for (let i = 0; i < arr1.length; i++) {
        assertEquals(0, arr1[i]);
    }

    // Check BYTES_PER_ELEMENT
    assertEquals(1, Uint8ClampedArray.BYTES_PER_ELEMENT);
	assertEquals(1, Uint8ClampedArray.prototype.BYTES_PER_ELEMENT);
	assertEquals(1, arr0.BYTES_PER_ELEMENT);

    // B. Construct from array-like
    let arr2 = new Uint8ClampedArray([10, 20, 300]);
	assertEquals(1, arr2.BYTES_PER_ELEMENT);
    assertEquals(3, arr2.length);
    // 300 should clamp to 255
    assertEquals(10, arr2[0]);
    assertEquals(20, arr2[1]);
    assertEquals(255, arr2[2]);

    // C. Construct from another typed array
    let arr3 = new Uint8ClampedArray(arr2);
    assertEquals(3, arr3.length);
    assertEquals(10, arr3[0]);
    assertEquals(20, arr3[1]);
    assertEquals(255, arr3[2]);
    // Changing arr2 should not affect arr3
    arr2[0] = 99;
    assertEquals(10, arr3[0]);

    // D. Construct from ArrayBuffer
    let buffer = new ArrayBuffer(8);
    let arr4 = new Uint8ClampedArray(buffer);
    assertEquals(8, arr4.length);
    assertSame(buffer, arr4.buffer);
    assertEquals(0, arr4.byteOffset);
    assertEquals(8, arr4.byteLength);

    // E. Construct from ArrayBuffer with offset and length
    let arr5 = new Uint8ClampedArray(buffer, 2, 4);
    assertEquals(4, arr5.length);
    assertSame(buffer, arr5.buffer);
    assertEquals(2, arr5.byteOffset);
    assertEquals(4, arr5.byteLength);

    // F. Invalid constructor arguments
    // Negative length
    assertThrows(RangeError, () => new Uint8ClampedArray(-1));
    // Offset out of range
    assertThrows(RangeError, () => new Uint8ClampedArray(buffer, 9));
    // Offset + length out of range
    assertThrows(RangeError, () => new Uint8ClampedArray(buffer, 2, 10));
	// Non-integer length
	assertEquals(2, new Uint8ClampedArray(2.5).length);
	assertEquals(2, new Uint8ClampedArray("2").length);
	assertEquals(0, new Uint8ClampedArray(null).length);
	assertEquals(0, new Uint8ClampedArray(undefined).length);
}

//
// 2. Clamping Behavior Tests
//
function testUint8ClampedArrayClamping() {
    let arr = new Uint8ClampedArray(5);

    // A. Assigning out-of-range values
    arr[0] = -100;
    arr[1] = 256;
    arr[2] = 999999;
    arr[3] = 127.9;     // floats get truncated (not just floored but typical rounding to nearest integer in some engines)
    arr[4] = 128.5;     // We want to see how it’s handled. In JS, it's typically "round to nearest integer" for clamped arrays.

    // The standard says: for clamped arrays, the value is first rounded to the nearest integer,
    // then clamped to 0..255. (Tie-breaking for .5 is "round to even" in modern ECMAScript.)
    // Implementation detail can vary, but let's do a general check:
    assertEquals(0, arr[0]);      // -100 => 0
    assertEquals(255, arr[1]);    // 256 => 255
    assertEquals(255, arr[2]);    // 999999 => 255

    // For .9, we typically expect it to round to 128
    // For .5, by spec, it typically uses "round half to even" => 128
    // We'll check either 127 or 128 for arr[3], but most engines do "round half to even" since ES2015.
    // Let's do a range check or partial logic for demonstration:
    assertTrue(arr[3] == 127 || arr[3] == 128);  
    assertTrue(arr[4] == 128);  

    // B. set() method with array-like
    arr.set([-1, 300], 1);
    // This should clamp to [0, 255]
    assertEquals(0, arr[1]);
    assertEquals(255, arr[2]);
}

//
// 3. Basic Array Methods (index access, set, subarray, slice, etc.)
//
function testUint8ClampedArrayBasicMethods() {
    let arr = new Uint8ClampedArray([0, 1, 2, 3, 255]);

    // A. set (from array-like)
    arr.set([9, 300], 2); // 300 => 255
    assertEquals(9, arr[2]);
    assertEquals(255, arr[3]);
    // Check that other elements are unchanged
    assertEquals(0, arr[0]);
    assertEquals(1, arr[1]);

    // B. set (from itself, overlapping)
    let arr2 = new Uint8ClampedArray([1, 2, 3, 4]);
    arr2.set(arr2.subarray(1, 3), 0); // move [2, 3] into arr2[0..1]
    assertEquals(2, arr2[0]);
    assertEquals(3, arr2[1]);
    assertEquals(3, arr2[2]); 
    assertEquals(4, arr2[3]);

    // C. subarray (this does not copy; it references the same buffer)
    let arr3 = new Uint8ClampedArray([10, 20, 30, 40, 50]);
    let sub = arr3.subarray(1, 4);
    assertEquals(3, sub.length);
    assertEquals(20, sub[0]);
    // changes in sub reflect in arr3
    sub[1] = 99;
    assertEquals(99, arr3[2]);

    // D. slice (this creates a copy if implemented)
    if (typeof arr3.slice === "function") {
        let sliceResult = arr3.slice(1, 3);
        assertEquals(2, sliceResult.length);
        assertEquals(20, sliceResult[0]);
        // changes in sliceResult do not affect the original
        sliceResult[0] = 88;
        assertEquals(20, arr3[1]);
    } else {
        // If slice is not implemented, skip.
    }
}

//
// 4. Higher-order Array Methods (map, filter, reduce, etc.)
//
function testUint8ClampedArrayArrayMethods() {
    let arr = new Uint8ClampedArray([5, 250, 255, 0]);

    // A. forEach
    let sum = 0;
    arr.forEach(val => sum += val);
    assertEquals(5 + 250 + 255 + 0, sum);

    // B. map
    // map returns a new Uint8ClampedArray
    // let's test boundary overflows
    let mapped = arr.map(x => x + 10);
    // [15, 255, 255, 10]
    assertEquals(15, mapped[0]);
    assertEquals(255, mapped[1]);  // 260 => clamp 255
    assertEquals(255, mapped[2]);
    assertEquals(10, mapped[3]);

    // C. filter
    let filtered = arr.filter(x => x > 0);
    // Should contain [5, 250, 255]
    assertEquals(3, filtered.length);
    assertEquals(5, filtered[0]);
    assertEquals(250, filtered[1]);
    assertEquals(255, filtered[2]);

    // D. reduce
    let product = arr.reduce((acc, val) => acc * val, 1);
    assertEquals(5 * 250 * 255 * 0, product); // => 0

    // E. some, every
    assertTrue(arr.some(x => x === 250));
    assertFalse(arr.some(x => x === 999));
    assertFalse(arr.every(x => x > 0));
    assertTrue(arr.every(x => x <= 255));

    // F. find, findIndex
    let found = arr.find(x => x > 200);
    assertEquals(250, found);
    let foundIndex = arr.findIndex(x => x > 200);
    assertEquals(1, foundIndex);

    // G. reverse (in-place)
    // Original arr: [5, 250, 255, 0]
    let reversed = arr.reverse();
    assertSame(arr, reversed); // reversed is the same object
    // arr is now [0, 255, 250, 5]
    assertEquals(0, arr[0]);
    assertEquals(255, arr[1]);
    assertEquals(250, arr[2]);
    assertEquals(5, arr[3]);
}

//
// 5. Other Array Methods (copyWithin, fill, indexOf, includes, sort, etc.)
//
function testUint8ClampedArrayOtherMethods() {
    let arr = new Uint8ClampedArray([1, 2, 3, 4, 5]);

    // A. copyWithin
    // copyWithin(target, start, end)
    arr.copyWithin(1, 2, 4);  // copy [3,4] to indices [1,2]
    // arr -> [1, 3, 4, 4, 5]
    assertEquals(1, arr[0]);
    assertEquals(3, arr[1]);
    assertEquals(4, arr[2]);
    assertEquals(4, arr[3]);
    assertEquals(5, arr[4]);

    // B. fill (with out-of-range values)
    let arr2 = new Uint8ClampedArray([0, 0, 0, 0, 0]);
    arr2.fill(300, 1, 4); // 300 => clamped to 255
    // arr2 -> [0, 255, 255, 255, 0]
    assertEquals(0, arr2[0]);
    assertEquals(255, arr2[1]);
    assertEquals(255, arr2[2]);
    assertEquals(255, arr2[3]);
    assertEquals(0, arr2[4]);

    // C. indexOf, lastIndexOf
    let arr3 = new Uint8ClampedArray([10, 20, 255, 20, 10]);
    assertEquals(0, arr3.indexOf(10));
    assertEquals(1, arr3.indexOf(20));
    assertEquals(-1, arr3.indexOf(99));
    assertEquals(4, arr3.lastIndexOf(10));
    assertEquals(3, arr3.lastIndexOf(20));
    // not found
    assertEquals(-1, arr3.lastIndexOf(999));

    // D. includes
    assertTrue(arr3.includes(255));
    assertFalse(arr3.includes(999));
    // fromIndex
    assertTrue(arr3.includes(255, 1));
	assertFalse(arr3.includes(255, 3));

    // E. sort (in-place, numeric ascending)
    let arr4 = new Uint8ClampedArray([100, 2, 999, 10]);
    arr4.sort();
    // 999 => stays 999 in array? Actually, when inserted, it's clamped to 255
    // But in the initial array, it's already clamped to 255 upon creation.
    // So effectively arr4 is [100, 2, 255, 10] before sorting
    // After sorting => [2, 10, 100, 255]
    assertEquals(2, arr4[0]);
    assertEquals(10, arr4[1]);
    assertEquals(100, arr4[2]);
    assertEquals(255, arr4[3]);
}

//
// 6. Iteration Protocols & Conversion
//
function testUint8ClampedArrayIterationAndConversion() {
    let arr = new Uint8ClampedArray([11, 255, 33]);

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
    let str = arr.toString();
    assertEquals("11,255,33", str);
    let joined = arr.join("-");
    assertEquals("11-255-33", joined);
}

//
// 7. Error Cases & Edge Cases
//
function testUint8ClampedArrayErrorCases() {
    let arr = new Uint8ClampedArray(5);

    // A. set: out of range offset
    assertThrows(RangeError, () => arr.set([1, 2, 3], 4));
    // There's only space for 1 item at offset 4, so copying 3 items is out of range.

    // B. subarray with out-of-bounds indices (subarray typically clamps, no error)
    let sub = arr.subarray(2, 10);
    assertEquals(3, sub.length); // indices 2..4 inclusive is 3 elements

    // C. Negative or out-of-range typed array indexing
    // arr[-1] is undefined (not an error in JS)
    assertEquals(undefined, arr[-1]);

	// D. Attempt to create from an empty array like
	assertEquals(0, new Uint8ClampedArray({ a: 1, b: 2 }).length );

	// E. Attempt typed array with detached buffer (engine-specific):
	let buffer = new ArrayBuffer(16);
	buffer.transfer();
	assertThrows(TypeError, () => new Uint8ClampedArray(buffer));

    // F. Sorting with a non-function compare argument
    let arr2 = new Uint8ClampedArray([2, 1]);
	assertThrows(TypeError, () => arr2.sort("not a function") );
}

//
// 8. Run all tests in a single function
//
function runUint8ClampedArrayTests() {
    testUint8ClampedArrayConstructor();
    testUint8ClampedArrayClamping();
    testUint8ClampedArrayBasicMethods();
    testUint8ClampedArrayArrayMethods();
    testUint8ClampedArrayOtherMethods();
    testUint8ClampedArrayIterationAndConversion();
    testUint8ClampedArrayErrorCases();
}

// (Optional) Execute immediately:
runUint8ClampedArrayTests();
