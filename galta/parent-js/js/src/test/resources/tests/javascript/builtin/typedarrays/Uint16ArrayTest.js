//
// 1. Constructor Tests and Basic Properties
//
function testUint16ArrayConstructor() {
	let arr0 = new Uint16Array();
	assertEquals(0, arr0.length);
	assertEquals('[object Uint16Array]', Object.prototype.toString.call(arr0));

	// A. Construct with length
    let arr1 = new Uint16Array(5);
    assertEquals(5, arr1.length);
    // All elements should be 0 by default
    for (let i = 0; i < arr1.length; i++) {
        assertEquals(0, arr1[i]);
    }

    // BYTES_PER_ELEMENT should be 2 for 16-bit unsigned
    assertEquals(2, Uint16Array.BYTES_PER_ELEMENT);
	assertEquals(2, Uint16Array.prototype.BYTES_PER_ELEMENT);
	assertEquals(2, arr0.BYTES_PER_ELEMENT);

    // B. Construct from array-like
    let arr2 = new Uint16Array([10, 65535, 65536]);
	assertEquals(2, arr2.BYTES_PER_ELEMENT);
    // 65536 mod 65536 = 0, so the last element should become 0
    assertEquals(3, arr2.length);
    assertEquals(10, arr2[0]);
    assertEquals(65535, arr2[1]);
    assertEquals(0, arr2[2]);

    // C. Construct from another typed array
    let arr3 = new Uint16Array(arr2);
    assertEquals(3, arr3.length);
    assertEquals(10, arr3[0]);
    assertEquals(65535, arr3[1]);
    assertEquals(0, arr3[2]);
    // Changing arr2 should not affect arr3
    arr2[0] = 999;
    assertEquals(10, arr3[0]);

    // D. Construct from ArrayBuffer
    //   Suppose buffer size is 8 => can hold 4 uint16 elements
    let buffer = new ArrayBuffer(8);
    let arr4 = new Uint16Array(buffer);
    assertEquals(4, arr4.length);
    assertSame(buffer, arr4.buffer);
    assertEquals(0, arr4.byteOffset);
    assertEquals(8, arr4.byteLength);

    // E. Construct from ArrayBuffer with offset and length
    //   offset=2, length=3 => total 2 + (3*2) = 2+6=8 => fits exactly
    let arr5 = new Uint16Array(buffer, 2, 3);
    assertEquals(3, arr5.length);
    assertSame(buffer, arr5.buffer);
    assertEquals(2, arr5.byteOffset);
    assertEquals(6, arr5.byteLength);

    // F. Invalid constructor arguments
    // Negative length
    assertThrows(RangeError, () => new Uint16Array(-1));
    // Offset out of range
    assertThrows(RangeError, () => new Uint16Array(buffer, 9));
    // Offset+length out of range
    assertThrows(RangeError, () => new Uint16Array(buffer, 2, 10));
	// Non-integer length
	assertEquals(2, new Uint16Array(2.5).length);
	assertEquals(2, new Uint16Array("2").length);
	assertEquals(0, new Uint16Array(null).length);
	assertEquals(0, new Uint16Array(undefined).length);
}

//
// 2. Basic Array Operations (index assignment, set, subarray, slice, etc.)
//
function testUint16ArrayBasicMethods() {
    // A. Basic index assignment with wrap-around for out-of-range 16-bit
    let arr = new Uint16Array(3);
    arr[0] = 65535;  // max 16-bit => 65535
    arr[1] = 65536;  // => 0 (wrap-around)
    arr[2] = 65537;  // => 1
    assertEquals(65535, arr[0]);
    assertEquals(0, arr[1]);
    assertEquals(1, arr[2]);

    // B. set (from array-like)
    let arr2 = new Uint16Array(4);
    arr2.set([10, 65535, 65536, 99999]);
    // 65536 => 0, 99999 mod 65536 => 34463
    assertEquals(10, arr2[0]);
    assertEquals(65535, arr2[1]);
    assertEquals(0, arr2[2]);
    assertEquals(34463, arr2[3]);

    // C. Overlapping set
    let arr3 = new Uint16Array([1, 2, 3, 4]);
    arr3.set(arr3.subarray(1, 3), 0);
    // copy [2, 3] into arr3[0..1] => [2, 3, 3, 4]
    assertEquals(2, arr3[0]);
    assertEquals(3, arr3[1]);
    assertEquals(3, arr3[2]);
    assertEquals(4, arr3[3]);

    // D. subarray (same buffer, no copy)
    let arr4 = new Uint16Array([10, 20, 30, 40]);
    let sub = arr4.subarray(1, 3); // => [20, 30]
    assertEquals(2, sub.length);
    assertEquals(20, sub[0]);
    // changes in sub reflect in arr4
    sub[1] = 99999; // 99999 mod 65536 = 34463
    assertEquals(34463, arr4[2]);

    // E. slice (creates a copy, if implemented)
    if (typeof arr4.slice === "function") {
        let sliceResult = arr4.slice(1, 3);
        assertEquals(2, sliceResult.length);
        assertEquals(20, sliceResult[0]);
        // changes in sliceResult do not affect arr4
        sliceResult[0] = 8888;
        assertEquals(20, arr4[1]);
    }
}

//
// 3. Higher-Order Array Methods (map, filter, reduce, etc.)
//
function testUint16ArrayArrayMethods() {
    let arr = new Uint16Array([0, 100, 30000, 65535]);

    // A. forEach
    let sum = 0;
    arr.forEach(val => sum += val);
    // => 0 + 100 + 30000 + 65535 = 95635
    assertEquals(95635, sum);

    // B. map => returns new Uint16Array
    let mapped = arr.map(x => x + 1);
    // => [1, 101, 30001, 0 (65536 -> 0)]
    assertEquals(arr.length, mapped.length);
    assertEquals(1, mapped[0]);
    assertEquals(101, mapped[1]);
    assertEquals(30001, mapped[2]);
    assertEquals(0, mapped[3]);

    // C. filter => returns new Uint16Array
    let filtered = arr.filter(x => x > 100);
    // => [30000, 65535]
    assertEquals(2, filtered.length);
    assertEquals(30000, filtered[0]);
    assertEquals(65535, filtered[1]);

    // D. reduce
    let product = arr.reduce((acc, val) => acc * val, 1);
    // => 1*0*100*30000*65535 => 0
    assertEquals(0, product);

    // E. some, every
    assertTrue(arr.some(x => x === 65535));
    assertFalse(arr.some(x => x === 99999));
    assertFalse(arr.every(x => x > 0));
    assertTrue(arr.every(x => x < 65536));

    // F. find, findIndex
    let found = arr.find(x => x > 50000);
    assertEquals(65535, found);
    let foundIndex = arr.findIndex(x => x > 50000);
    assertEquals(3, foundIndex);

    // G. reverse (in-place)
    let reversed = arr.reverse();
    assertSame(arr, reversed);
    // original => [0, 100, 30000, 65535]
    // reversed => [65535, 30000, 100, 0]
    assertEquals(65535, arr[0]);
    assertEquals(30000, arr[1]);
    assertEquals(100, arr[2]);
    assertEquals(0, arr[3]);
}

//
// 4. Other Array Methods (copyWithin, fill, indexOf, includes, sort, etc.)
//
function testUint16ArrayOtherMethods() {
    let arr = new Uint16Array([1, 2, 3, 4, 5]);

    // A. copyWithin
    arr.copyWithin(1, 2, 4);  
    // => [1, 3, 4, 4, 5]
    assertEquals(1, arr[0]);
    assertEquals(3, arr[1]);
    assertEquals(4, arr[2]);
    assertEquals(4, arr[3]);
    assertEquals(5, arr[4]);

    // B. fill
    let arr2 = new Uint16Array(5);
    arr2.fill(99999, 1, 4);
    // 99999 mod 65536 => 34463
    // => [0, 34463, 34463, 34463, 0]
    assertEquals(0, arr2[0]);
    assertEquals(34463, arr2[1]);
    assertEquals(34463, arr2[2]);
    assertEquals(34463, arr2[3]);
    assertEquals(0, arr2[4]);

    // C. indexOf, lastIndexOf
    let arr3 = new Uint16Array([10, 20, 65535, 20, 10]);
    assertEquals(0, arr3.indexOf(10));
    assertEquals(1, arr3.indexOf(20));
    assertEquals(-1, arr3.indexOf(9999));
    assertEquals(4, arr3.lastIndexOf(10));
    assertEquals(3, arr3.lastIndexOf(20));

    // D. includes
    assertTrue(arr3.includes(65535));
    assertFalse(arr3.includes(99999));
    // fromIndex
	assertTrue(arr3.includes(10, 2));
	assertFalse(arr3.includes(30, 3));

    // E. sort (in-place, numeric ascending)
    let arr4 = new Uint16Array([100, 2, 65535, 10]);
    arr4.sort();
    // => [2, 10, 100, 65535]
    assertEquals(2, arr4[0]);
    assertEquals(10, arr4[1]);
    assertEquals(100, arr4[2]);
    assertEquals(65535, arr4[3]);
}

//
// 5. Iteration Protocols & Conversion
//
function testUint16ArrayIterationAndConversion() {
    let arr = new Uint16Array([11, 65535, 33]);

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
    // => "11,65535,33"
    let str = arr.toString();
    assertTrue(typeof str === "string");
    assertTrue(str.indexOf(",") >= 0);

    let joined = arr.join("-");
    // => "11-65535-33"
    assertTrue(typeof joined === "string");
    assertTrue(joined.indexOf("-") >= 0);
}

//
// 6. Error & Edge Cases
//
function testUint16ArrayErrorCases() {
    let arr = new Uint16Array(5);

    // A. set: out-of-range offset
    assertThrows(RangeError, () => arr.set([1, 2, 3], 4));
    // Only room for 1 element at index 4

    // B. Negative index => undefined
    assertEquals(undefined, arr[-1]);
    // large index => undefined
    assertEquals(undefined, arr[999]);

    // C. subarray with out-of-bounds => clamps
    let sub = arr.subarray(2, 10);
    assertEquals(3, sub.length);

	// D. Attempt to create from an empty array like
	assertEquals(0, new Uint16Array({ a: 1, b: 2 }).length );

	// E. Attempt typed array with detached buffer (engine-specific):
	let buffer = new ArrayBuffer(16);
	buffer.transfer();
	assertThrows(TypeError, () => new Uint16Array(buffer));
}

//
// 7. Run all tests
//
function runUint16ArrayTests() {
    testUint16ArrayConstructor();
    testUint16ArrayBasicMethods();
    testUint16ArrayArrayMethods();
    testUint16ArrayOtherMethods();
    testUint16ArrayIterationAndConversion();
    testUint16ArrayErrorCases();
}

// (Optional) Execute tests immediately:
runUint16ArrayTests();
