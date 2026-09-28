//
// 1. Constructor Tests and Basic Properties
//
function testInt8ArrayConstructor() {
	let arr0 = new Int8Array();
	assertEquals(0, arr0.length);
	assertEquals('[object Int8Array]', Object.prototype.toString.call(arr0));

	    // A. Construct with length
    let arr1 = new Int8Array(5);
    assertEquals(5, arr1.length);
    // All elements should be 0 by default
    for (let i = 0; i < arr1.length; i++) {
        assertEquals(0, arr1[i]);
    }

    // Check BYTES_PER_ELEMENT
    assertEquals(1, Int8Array.BYTES_PER_ELEMENT);
	assertEquals(1, Int8Array.prototype.BYTES_PER_ELEMENT);
	assertEquals(1, arr0.BYTES_PER_ELEMENT);

    // B. Construct from array-like
    let arr2 = new Int8Array([10, -20, 30]);
	assertEquals(1, arr2.BYTES_PER_ELEMENT);
    assertEquals(3, arr2.length);
    assertEquals(10, arr2[0]);
    assertEquals(-20, arr2[1]);
    assertEquals(30, arr2[2]);

    // C. Construct from another typed array
    let arr3 = new Int8Array(arr2);
    assertEquals(3, arr3.length);
    assertEquals(10, arr3[0]);
    assertEquals(-20, arr3[1]);
    assertEquals(30, arr3[2]);
    // Changing arr2 should not affect arr3
    arr2[0] = 99;
    assertEquals(10, arr3[0]);

    // D. Construct from ArrayBuffer
    let buffer = new ArrayBuffer(8);
    let arr4 = new Int8Array(buffer);
    assertEquals(8, arr4.length);
    assertSame(buffer, arr4.buffer);
    assertEquals(0, arr4.byteOffset);
    assertEquals(8, arr4.byteLength);

    // E. Construct from ArrayBuffer with offset and length
    let arr5 = new Int8Array(buffer, 2, 4);
    assertEquals(4, arr5.length);
    assertSame(buffer, arr5.buffer);
    assertEquals(2, arr5.byteOffset);
    assertEquals(4, arr5.byteLength);

    // F. Invalid constructor arguments
    // Negative length
    assertThrows(RangeError, () => new Int8Array(-1));
    // Offset out of range
    assertThrows(RangeError, () => new Int8Array(buffer, 9));
    // Offset + length out of range
    assertThrows(RangeError, () => new Int8Array(buffer, 2, 10));
	// Non-integer length
	assertEquals(2, new Int8Array(2.5).length);
	assertEquals(2, new Int8Array("2").length);
	assertEquals(0, new Int8Array(null).length);
	assertEquals(0, new Int8Array(undefined).length);
}

//
// 2. Basic Array Methods (index access, set, subarray, slice, etc.)
//
function testInt8ArrayBasicMethods() {
    let arr = new Int8Array([0, 1, -2, 3, -4, 5]);

    // A. set (from array-like)
    arr.set([9, -8], 2);
    assertEquals(9, arr[2]);
    assertEquals(-8, arr[3]);
    // Check that other elements are unchanged
    assertEquals(0, arr[0]);
    assertEquals(1, arr[1]);

    // B. set (from itself, overlapping)
    let arr2 = new Int8Array([1, 2, 3, 4]);
    arr2.set(arr2.subarray(1, 3), 0); // move [2,3] into arr2[0..1]
    assertEquals(2, arr2[0]);
    assertEquals(3, arr2[1]);
    assertEquals(3, arr2[2]); 
    assertEquals(4, arr2[3]);

    // C. subarray (this does not copy; it references the same buffer)
    let arr3 = new Int8Array([10, 20, -30, 40, 50]);
    let sub = arr3.subarray(1, 4);
    assertEquals(3, sub.length);
    assertEquals(20, sub[0]);
    // changes in sub reflect in arr3
    sub[1] = -99;
    assertEquals(-99, arr3[2]);

    // D. slice (this creates a copy in modern engines)
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
// 3. Higher-order Array Methods (map, filter, reduce, etc.)
//
function testInt8ArrayArrayMethods() {
    let arr = new Int8Array([-5, 10, -15, 20, 25]);

    // A. forEach
    let sum = 0;
    arr.forEach(val => sum += val);
    assertEquals(-5 + 10 + -15 + 20 + 25, sum);

    // B. map
    // NOTE: map returns a new Int8Array
    let mapped = arr.map(x => x + 1);
    assertEquals(arr.length, mapped.length);
    assertEquals(-4, mapped[0]);
    assertEquals(11, mapped[1]);
    assertEquals(-14, mapped[2]);
    // Original array should be unchanged
    assertEquals(-5, arr[0]);

    // C. filter
    let filtered = arr.filter(x => x > 0);
    // Should contain [10, 20, 25]
    assertEquals(3, filtered.length);
    assertEquals(10, filtered[0]);
    assertEquals(20, filtered[1]);
    assertEquals(25, filtered[2]);

    // D. reduce
    let product = arr.reduce((acc, val) => acc * val, 1);
    assertEquals(-5 * 10 * -15 * 20 * 25, product);

    // E. some, every
    assertTrue(arr.some(x => x === 10));
    assertFalse(arr.some(x => x === 99));
    assertFalse(arr.every(x => x > 0));
    assertTrue(arr.every(x => x % 5 === 0));  // all multiples of 5

    // F. find, findIndex
    let found = arr.find(x => x < 0);
    // The first negative is -5
    assertEquals(-5, found);
    let foundIndex = arr.findIndex(x => x < 0);
    assertEquals(0, foundIndex);

    // G. reverse (in-place)
    let reversed = arr.reverse();
    assertSame(arr, reversed); // reversed is the same object
    // Original was [-5, 10, -15, 20, 25]
    assertEquals(25, arr[0]);
    assertEquals(20, arr[1]);
    assertEquals(-15, arr[2]);
    assertEquals(10, arr[3]);
    assertEquals(-5, arr[4]);
}

//
// 4. Other Array Methods (copyWithin, fill, indexOf, includes, sort, etc.)
//
function testInt8ArrayOtherMethods() {
    let arr = new Int8Array([1, 2, 3, 4, 5]);

    // A. copyWithin
    // copyWithin(target, start, end)
    arr.copyWithin(1, 2, 4);  // copy [3,4] to indices [1,2]
    // arr -> [1, 3, 4, 4, 5]
    assertEquals(1, arr[0]);
    assertEquals(3, arr[1]);
    assertEquals(4, arr[2]);
    assertEquals(4, arr[3]);
    assertEquals(5, arr[4]);

    // B. fill
    let arr2 = new Int8Array([0, 0, 0, 0, 0]);
    arr2.fill(-9, 1, 4);
    // arr2 -> [0, -9, -9, -9, 0]
    assertEquals(0, arr2[0]);
    assertEquals(-9, arr2[1]);
    assertEquals(-9, arr2[2]);
    assertEquals(-9, arr2[3]);
    assertEquals(0, arr2[4]);

    // C. indexOf, lastIndexOf
    let arr3 = new Int8Array([-10, 20, 30, 20, -10]);
    assertEquals(0, arr3.indexOf(-10));
    assertEquals(1, arr3.indexOf(20));
    assertEquals(-1, arr3.indexOf(99));
    assertEquals(4, arr3.lastIndexOf(-10));
    assertEquals(3, arr3.lastIndexOf(20));
    // not found
    assertEquals(-1, arr3.lastIndexOf(99));

    // D. includes
    assertTrue(arr3.includes(30));
    assertFalse(arr3.includes(99));
    // fromIndex
	assertTrue(arr3.includes(30, 2));
	assertFalse(arr3.includes(20, 4));

    // E. sort (in-place, numeric ascending)
    let arr4 = new Int8Array([100, 2, -99, 10]);
    arr4.sort();
    // [ -99, 2, 10, 100 ]
    assertEquals(-99, arr4[0]);
    assertEquals(2, arr4[1]);
    assertEquals(10, arr4[2]);
    assertEquals(100, arr4[3]);
}

//
// 5. Iteration Protocols & Conversion
//
function testInt8ArrayIterationAndConversion() {
    let arr = new Int8Array([11, -22, 33]);

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
    // For typed arrays, toString often behaves like Array.prototype.toString
    let str = arr.toString();
    assertEquals("11,-22,33", str);
    let joined = arr.join("-");
    assertEquals("11--22-33", joined);
}

//
// 6. Error Cases & Edge Cases
//
function testInt8ArrayErrorCases() {
    let arr = new Int8Array(5);

    // A. set: out of range offset
    assertThrows(RangeError, () => arr.set([1, 2, 3], 4)); 
    // Only 1 space left at offset 4 (index 4), so trying to copy 3 items is out of range.

    // B. subarray with out-of-bounds indices (subarray typically clamps, no error)
    let sub = arr.subarray(2, 10);
    assertEquals(3, sub.length); // 2..4 inclusive is 3 elements

    // C. Negative or out-of-range typed array indexing
    // arr[-1] is undefined (not an error in JS)
    assertEquals(undefined, arr[-1]);

	// D. Attempt to create from an empty array like
	assertEquals(0, new Int8Array({ a: 1, b: 2 }).length );

	// E. Attempt typed array with detached buffer (engine-specific):
	let buffer = new ArrayBuffer(16);
	buffer.transfer();
	assertThrows(TypeError, () => new Int8Array(buffer));

    // F. Sorting with a non-function compare argument
    let arr2 = new Int8Array([2, 1]);
	assertThrows(TypeError, () => arr2.sort("not a function") );

	// G. Assigning a value whose magnitude exceeds Integer.MAX_VALUE must
	// wrap modulo 2^8, not saturate. Number.byteValue()'s default
	// implementation narrows via intValue() first, which for a Double
	// SATURATES to Integer.MAX_VALUE/MIN_VALUE instead of wrapping.
	arr[0] = 0xF7F7F7F7;
	assertEquals(-9, arr[0]); // low byte of 0xF7F7F7F7 is 0xF7 = -9 signed
}

//
// 7. %TypedArray%.of and buffer-arg ToIndex/detached-buffer ordering
// (shared TypedArrayConstructor base class behavior, exercised here via
// Int8Array but applicable to every typed array type).
//
function testInt8ArrayOfAndBufferArg() {
	// %TypedArray%.of was declared but never actually registered on any
	// concrete constructor - calling it threw "not a function" entirely.
	let a = Int8Array.of(1, 2, 3);
	assertEquals(3, a.length);
	assertEquals(1, a[0]);
	assertEquals(2, a[1]);
	assertEquals(3, a[2]);

	// %TypedArray%.of uses `this` as the constructor (TypedArrayCreate),
	// not the constructor it was originally defined on.
	let calls = 0;
	function Ctor(len) {
		calls++;
		return new Int8Array(len);
	}
	let b = Int8Array.of.call(Ctor, 5, 6);
	assertTrue(b instanceof Int8Array);
	assertEquals(1, calls);
	assertEquals(5, b[0]);
	assertEquals(6, b[1]);

	assertThrows(TypeError, () => Int8Array.of.call({}, 1));

	// ToIndex(byteOffset)/ToIndex(length) on the buffer-arg constructor:
	// values coerce via valueOf/toString, undefined defaults, and
	// non-multiple-of-BYTES_PER_ELEMENT offsets throw RangeError (checked
	// on Int16Array, where BYTES_PER_ELEMENT is 2).
	let buf = new ArrayBuffer(8);
	let view = new Int16Array(buf, { valueOf: () => 2 });
	assertEquals(2, view.byteOffset);
	assertEquals(3, view.length);
	assertThrows(RangeError, () => new Int16Array(buf, 1));
}

//
// 8. Run all tests in a single function
//
function runInt8ArrayTests() {
    testInt8ArrayConstructor();
    testInt8ArrayBasicMethods();
    testInt8ArrayArrayMethods();
    testInt8ArrayOtherMethods();
    testInt8ArrayIterationAndConversion();
    testInt8ArrayErrorCases();
    testInt8ArrayOfAndBufferArg();
}

// (Optional) Execute immediately:
runInt8ArrayTests();
