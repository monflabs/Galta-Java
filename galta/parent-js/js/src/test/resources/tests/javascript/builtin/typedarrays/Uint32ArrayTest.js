//
// 1. Constructor Tests and Basic Properties
//
function testUint32ArrayConstructor() {
	let arr0 = new Uint32Array();
	assertEquals(0, arr0.length);
	assertEquals('[object Uint32Array]', Object.prototype.toString.call(arr0));
	
    // A. Construct with length
    let arr1 = new Uint32Array(5);
    assertEquals(5, arr1.length);
    // All elements should be 0 by default
    for (let i = 0; i < arr1.length; i++) {
        assertEquals(0, arr1[i]);
    }

    // BYTES_PER_ELEMENT should be 4 for 32-bit unsigned
    assertEquals(4, Uint32Array.BYTES_PER_ELEMENT);
	assertEquals(4, Uint32Array.prototype.BYTES_PER_ELEMENT);
	assertEquals(4, arr0.BYTES_PER_ELEMENT);

    // B. Construct from array-like
    let arr2 = new Uint32Array([10, 4294967295, 4294967296]); 
	assertEquals(4, arr2.BYTES_PER_ELEMENT);
    // 4294967296 mod 2^32 => 0
    // so the last element should become 0
    assertEquals(3, arr2.length);
    assertEquals(10, arr2[0]);
    // 4294967295 is 0xFFFFFFFF => 2^32 - 1 => 4294967295 in decimal
    assertEquals(4294967295, arr2[1]);
    assertEquals(0, arr2[2]);

    // C. Construct from another typed array
    let arr3 = new Uint32Array(arr2);
    assertEquals(3, arr3.length);
    assertEquals(10, arr3[0]);
    assertEquals(4294967295, arr3[1]);
    assertEquals(0, arr3[2]);
    // Changing arr2 should not affect arr3
    arr2[0] = 999;
    assertEquals(10, arr3[0]);

    // D. Construct from ArrayBuffer
    //   Suppose buffer size is 16 => can hold 4 Uint32 elements
    let buffer = new ArrayBuffer(16);
    let arr4 = new Uint32Array(buffer);
    assertEquals(4, arr4.length);
    assertSame(buffer, arr4.buffer);
    assertEquals(0, arr4.byteOffset);
    assertEquals(16, arr4.byteLength);

    // E. Construct from ArrayBuffer with offset and length
    //   offset=8 => length=2 => total 8+(2*4)=16 => fits exactly
    let arr5 = new Uint32Array(buffer, 8, 2);
    assertEquals(2, arr5.length);
    assertSame(buffer, arr5.buffer);
    assertEquals(8, arr5.byteOffset);
    assertEquals(8, arr5.byteLength);

    // F. Invalid constructor arguments
    // Negative length
    assertThrows(RangeError, () => new Uint32Array(-1));
    // Offset out of range
    assertThrows(RangeError, () => new Uint32Array(buffer, 17));
    // Offset + length out of range
    assertThrows(RangeError, () => new Uint32Array(buffer, 0, 5)); // 5*4=20 => beyond buffer of 16
	// Non-integer length
	assertEquals(2, new Uint32Array(2.5).length);
	assertEquals(2, new Uint32Array("2").length);
	assertEquals(0, new Uint32Array(null).length);
	assertEquals(0, new Uint32Array(undefined).length);
}

//
// 2. Basic Array Operations (index assignment, set, subarray, slice, etc.)
//
function testUint32ArrayBasicMethods() {
    // A. Basic index assignment with wrap-around for out-of-range 32-bit
    let arr = new Uint32Array(3);
    arr[0] = 4294967295;  // => 0xFFFFFFFF => 4294967295
    arr[1] = 4294967296;  // => mod 2^32 => 0
    arr[2] = 4294967297;  // => mod 2^32 => 1
    assertEquals(4294967295, arr[0]);
    assertEquals(0, arr[1]);
    assertEquals(1, arr[2]);

    // B. set (from array-like)
    let arr2 = new Uint32Array(4);
    arr2.set([10, 4294967295, 4294967296, 9999999999]);
    // 4294967296 => 0
    // 9999999999 mod 2^32 => let's see: 9999999999 % 4294967296 => 1410065407
    assertEquals(10, arr2[0]);
    assertEquals(4294967295, arr2[1]);
    assertEquals(0, arr2[2]);
    assertEquals(1410065407, arr2[3]);

    // C. Overlapping set
    let arr3 = new Uint32Array([1, 2, 3, 4]);
    arr3.set(arr3.subarray(1, 3), 0);
    // copy [2,3] to arr3[0..1] => [2,3,3,4]
    assertEquals(2, arr3[0]);
    assertEquals(3, arr3[1]);
    assertEquals(3, arr3[2]);
    assertEquals(4, arr3[3]);

    // D. subarray (same buffer, no copy)
    let arr4 = new Uint32Array([10, 20, 30, 40]);
    let sub = arr4.subarray(1, 3); // => [20, 30]
    assertEquals(2, sub.length);
    assertEquals(20, sub[0]);
    // changes in sub reflect in arr4
    sub[1] = 9999999999; // => mod 2^32 => 1410065407
    assertEquals(1410065407, arr4[2]);

    // E. slice (creates a copy, if implemented)
    if (typeof arr4.slice === "function") {
        let sliceResult = arr4.slice(1, 3);
        assertEquals(2, sliceResult.length);
        assertEquals(20, sliceResult[0]);
        // changes in sliceResult do not affect arr4
        sliceResult[0] = 1234;
        assertEquals(20, arr4[1]);
    }
}

//
// 3. Higher-Order Array Methods (map, filter, reduce, etc.)
//
function testUint32ArrayArrayMethods() {
    let arr = new Uint32Array([0, 100, 3000000000, 4294967295]);

    // A. forEach
    let sum = 0;
    arr.forEach(val => sum += val);
    // => 0 + 100 + 3000000000 + 4294967295 = 7294967395
    // This sum is beyond 2^32, but in JS we store it in normal Number. 
    assertTrue(sum > 4.294967295e9);

    // B. map => returns a new Uint32Array
    let mapped = arr.map(x => x + 1);
    // [1, 101, 3000000001, 0 => (4294967295 + 1 => 4294967296 => mod => 0)]
    assertEquals(arr.length, mapped.length);
    assertEquals(1, mapped[0]);
    assertEquals(101, mapped[1]);
    assertEquals(3000000001, mapped[2]);
    assertEquals(0, mapped[3]);

    // C. filter => new Uint32Array
    let filtered = arr.filter(x => x > 1000);
    // => [3000000000, 4294967295]
    assertEquals(2, filtered.length);
    assertEquals(3000000000, filtered[0]);
    assertEquals(4294967295, filtered[1]);

    // D. reduce
    let product = arr.reduce((acc, val) => acc * val, 1);
    // => 1 * 0 => 0
    assertEquals(0, product);

    // E. some, every
    assertTrue(arr.some(x => x === 4294967295));
    assertFalse(arr.some(x => x === 4294967296));
    assertFalse(arr.every(x => x > 0));
    assertTrue(arr.every(x => x < 4294967296));

    // F. find, findIndex
    let found = arr.find(x => x > 2000000000);
    assertEquals(3000000000, found);
    let foundIndex = arr.findIndex(x => x > 2000000000);
    assertEquals(2, foundIndex);

    // G. reverse (in-place)
    // original => [0, 100, 3000000000, 4294967295]
    let reversed = arr.reverse();
    // => [4294967295, 3000000000, 100, 0]
    assertSame(arr, reversed);
    assertEquals(4294967295, arr[0]);
    assertEquals(3000000000, arr[1]);
    assertEquals(100, arr[2]);
    assertEquals(0, arr[3]);
}

//
// 4. Other Array Methods (copyWithin, fill, indexOf, includes, sort, etc.)
//
function testUint32ArrayOtherMethods() {
    let arr = new Uint32Array([1, 2, 3, 4, 5]);

    // A. copyWithin
    arr.copyWithin(1, 2, 4);
    // => [1, 3, 4, 4, 5]
    assertEquals(1, arr[0]);
    assertEquals(3, arr[1]);
    assertEquals(4, arr[2]);
    assertEquals(4, arr[3]);
    assertEquals(5, arr[4]);

    // B. fill
    let arr2 = new Uint32Array(5);
    arr2.fill(9999999999, 1, 4);
    // 9999999999 mod 2^32 => 1410065407
    // => [0, 1410065407, 1410065407, 1410065407, 0]
    assertEquals(0, arr2[0]);
    assertEquals(1410065407, arr2[1]);
    assertEquals(1410065407, arr2[2]);
    assertEquals(1410065407, arr2[3]);
    assertEquals(0, arr2[4]);

    // C. indexOf, lastIndexOf
    let arr3 = new Uint32Array([10, 20, 4294967295, 20, 10]);
    assertEquals(0, arr3.indexOf(10));
    assertEquals(1, arr3.indexOf(20));
    assertEquals(-1, arr3.indexOf(99999));
    assertEquals(4, arr3.lastIndexOf(10));
    assertEquals(3, arr3.lastIndexOf(20));

    // D. includes
    assertTrue(arr3.includes(4294967295));
    assertFalse(arr3.includes(9999999999));
    // fromIndex
    assertTrue(arr3.includes(4294967295, 2));
	assertFalse(arr3.includes(4294967295, 3));

    // E. sort (in-place, numeric ascending)
    let arr4 = new Uint32Array([100, 2, 4294967295, 10]);
    arr4.sort();
    // => [2, 10, 100, 4294967295]
    assertEquals(2, arr4[0]);
    assertEquals(10, arr4[1]);
    assertEquals(100, arr4[2]);
    assertEquals(4294967295, arr4[3]);
}

//
// 5. Iteration Protocols & Conversion
//
function testUint32ArrayIterationAndConversion() {
    let arr = new Uint32Array([11, 4294967295, 33]);

    // A. entries()
    let entries = arr.entries();
    let e1 = entries.next();
    assertFalse(e1.done);
    // e1.value => [0, 11]
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
    let iter = arr[Symbol.iterator](); // same as values()
    let i1 = iter.next();
    assertEquals(11, i1.value);

    // D. toString / join
    let str = arr.toString();
    // => "11,4294967295,33"
    assertTrue(typeof str === "string");
    assertTrue(str.indexOf(",") >= 0);

    let joined = arr.join("-");
    // => "11-4294967295-33"
    assertTrue(typeof joined === "string");
    assertTrue(joined.indexOf("-") >= 0);
}

//
// 6. Error & Edge Cases
//
function testUint32ArrayErrorCases() {
    let arr = new Uint32Array(5);

    // A. set: out-of-range offset
    assertThrows(RangeError, () => arr.set([1, 2, 3], 4));
    // Only 1 index available at offset 4, so copying 3 items is out-of-range

    // B. Negative or large index => undefined
    assertEquals(undefined, arr[-1]);
    assertEquals(undefined, arr[999]);

    // C. subarray with out-of-bounds => clamps
    let sub = arr.subarray(3, 10);
    assertEquals(2, sub.length); // from index 3..4 => 2 elements

	// D. Attempt to create from an empty array like
	assertEquals(0, new Uint32Array({ a: 1, b: 2 }).length );

	// E. Attempt typed array with detached buffer (engine-specific):
	let buffer = new ArrayBuffer(16);
	buffer.transfer();
	assertThrows(TypeError, () => new Uint32Array(buffer));
}

//
// 7. Run all tests
//
function runUint32ArrayTests() {
    testUint32ArrayConstructor();
    testUint32ArrayBasicMethods();
    testUint32ArrayArrayMethods();
    testUint32ArrayOtherMethods();
    testUint32ArrayIterationAndConversion();
    testUint32ArrayErrorCases();
}

// (Optional) Execute tests now:
runUint32ArrayTests();
