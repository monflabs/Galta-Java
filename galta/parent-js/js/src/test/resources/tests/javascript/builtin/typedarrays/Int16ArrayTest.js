//
// 1. Constructor Tests and Basic Properties
//
function testInt16ArrayConstructor() {
	let arr0 = new Int16Array();
	assertEquals(0, arr0.length);
	assertEquals('[object Int16Array]', Object.prototype.toString.call(arr0));

	    // A. Construct with length
    let arr1 = new Int16Array(5);
    assertEquals(5, arr1.length);
    // All elements should be 0 by default
    for (let i = 0; i < arr1.length; i++) {
        assertEquals(0, arr1[i]);
    }

    // Check BYTES_PER_ELEMENT
    assertEquals(2, Int16Array.BYTES_PER_ELEMENT);
	assertEquals(2, Int16Array.prototype.BYTES_PER_ELEMENT);
	assertEquals(2, arr0.BYTES_PER_ELEMENT);

    // B. Construct from array-like
    let arr2 = new Int16Array([10, -20, 30000]);
    assertEquals(3, arr2.length);
    assertEquals(10, arr2[0]);
    assertEquals(-20, arr2[1]);
    // 30000 is within the range of Int16 (-32768..32767),
    // so it remains 30000 => -35536 overflow if beyond 32767,
    // but 30000 is valid. So let's confirm:
    assertEquals(30000, arr2[2]);

    // C. Construct from another typed array
    let arr3 = new Int16Array(arr2);
    assertEquals(3, arr3.length);
    assertEquals(10, arr3[0]);
    assertEquals(-20, arr3[1]);
    assertEquals(30000, arr3[2]);
    // Changing arr2 should not affect arr3
    arr2[0] = 9999;
    assertEquals(10, arr3[0]);

    // D. Construct from ArrayBuffer
    //   Suppose buffer size is 8 bytes => can hold 4 int16 elements
    let buffer = new ArrayBuffer(8);
    let arr4 = new Int16Array(buffer);
    assertEquals(4, arr4.length);
    assertSame(buffer, arr4.buffer);
    assertEquals(0, arr4.byteOffset);
    assertEquals(8, arr4.byteLength);

    // E. Construct from ArrayBuffer with offset and length
    //   Each int16 is 2 bytes. offset=2 => not aligned for 2 bytes in some engines,
    //   but let's assume 2 is acceptable if the environment doesn't strictly require alignment.
    //   length=3 => 3 * 2 = 6 bytes, from offset=2 => total 8 bytes => offset+length is 8, fits exactly
    let arr5 = new Int16Array(buffer, 2, 3);
    assertEquals(3, arr5.length);
    assertSame(buffer, arr5.buffer);
    assertEquals(2, arr5.byteOffset);
    assertEquals(6, arr5.byteLength);

    // F. Invalid constructor arguments
    // Negative length
    assertThrows(RangeError, () => new Int16Array(-1));
    // Offset out of range
    assertThrows(RangeError, () => new Int16Array(buffer, 9));
    // Offset + length out of range
    assertThrows(RangeError, () => new Int16Array(buffer, 2, 10));
	// Non-integer length
	assertEquals(2, new Int16Array(2.5).length);
	assertEquals(2, new Int16Array("2").length);
	assertEquals(0, new Int16Array(null).length);
	assertEquals(0, new Int16Array(undefined).length);
}

//
// 2. Basic Array Operations (index assignment, set, subarray, slice, etc.)
//
function testInt16ArrayBasicMethods() {
    // A. Basic index assignment with wrap-around for out-of-range
    //   Int16 range: -32768..32767
    let arr = new Int16Array(3);
    arr[0] = 32767;
    arr[1] = 32768;  // => -32768 (overflow by 1)
    arr[2] = -32769; // => 32767 (underflow by 1)
    assertEquals(32767, arr[0]);
    assertEquals(-32768, arr[1]);
    assertEquals(32767, arr[2]);

    // B. set (from array-like)
    let arr2 = new Int16Array(4);
    arr2.set([10, -20, 30000, 32768]);  // 32768 => -32768
    assertEquals(10, arr2[0]);
    assertEquals(-20, arr2[1]);
    assertEquals(30000, arr2[2]);
    assertEquals(-32768, arr2[3]);

    // C. Overlapping set
    let arr3 = new Int16Array([1, 2, 3, 4]);
    arr3.set(arr3.subarray(1, 3), 0); 
    // copy [2,3] to arr3[0..1]
    // => [2, 3, 3, 4]
    assertEquals(2, arr3[0]);
    assertEquals(3, arr3[1]);
    assertEquals(3, arr3[2]);
    assertEquals(4, arr3[3]);

    // D. subarray (same buffer, no copy)
    let arr4 = new Int16Array([10, 20, 30, 40]);
    let sub = arr4.subarray(1, 3); // => [20,30]
    assertEquals(2, sub.length);
    assertEquals(20, sub[0]);
    // changes in sub reflect in arr4
    sub[1] = -9999; 
    // -9999 mod 65536 => 55537 => -9999 in signed 16 => -9999 % 2^16 => 55537
    // actual stored => -9999 & 0xFFFF => 55537 => in int16 => -9999 + possibly modulo
    assertEquals(-9999, arr4[2]);

    // E. slice (creates a copy, if implemented)
    if (typeof arr4.slice === "function") {
        let sliceResult = arr4.slice(1, 3);
        // => new Int16Array of length 2
        assertEquals(2, sliceResult.length);
        assertEquals(20, sliceResult[0]);
        // changes in sliceResult do not affect arr4
        sliceResult[0] = 9999;
        // 9999 in int16 => 9999 => okay
        assertEquals(20, arr4[1]);
    } 
}

//
// 3. Higher-Order Array Methods (map, filter, reduce, etc.)
//
function testInt16ArrayArrayMethods() {
    let arr = new Int16Array([-32768, 10, 200, 30000]);

    // A. forEach
    let sum = 0;
    arr.forEach(val => sum += val);
    // => -32768 + 10 + 200 + 30000 => -32768 + 30210 => -2558
    assertEquals(-2558, sum);

    // B. map => new Int16Array
    let mapped = arr.map(x => x + 1);
    // [-32767, 11, 201, 30001], but watch for overflow if 30001 > 32767 => it's okay, 30001 < 32767
    assertEquals(-32767, mapped[0]);
    assertEquals(11, mapped[1]);
    assertEquals(201, mapped[2]);
    assertEquals(30001, mapped[3]);

    // C. filter => new Int16Array
    let filtered = arr.filter(x => x > 0);
    // => [10, 200, 30000]
    assertEquals(3, filtered.length);
    assertEquals(10, filtered[0]);
    assertEquals(200, filtered[1]);
    assertEquals(30000, filtered[2]);

    // D. reduce
    let product = arr.reduce((acc, val) => acc * val, 1);
    // => 1 * (-32768) * 10 * 200 * 30000 => huge number => wraps in JS, but we do normal numeric ops in JS
    // For the sake of typed array, we store in normal JS let, no overflow in JS Number except maybe Infinity. 
    // -32768 * 10 => -327680; *200 => -65536000; *30000 => -1.96608e12
    assertEquals(-1966080000000, product); // -1.96608e12

    // E. some, every
    assertTrue(arr.some(x => x === 10));
    assertFalse(arr.some(x => x === 9999));
    assertFalse(arr.every(x => x > 0));
    assertTrue(arr.every(x => x % 1 === 0)); // all are integers

    // F. find, findIndex
    let found = arr.find(x => x > 100);
    assertEquals(200, found);
    let foundIndex = arr.findIndex(x => x > 100);
    assertEquals(2, foundIndex);

    // G. reverse (in-place)
    // arr => [-32768, 10, 200, 30000]
    let reversed = arr.reverse();
    assertSame(arr, reversed);
    // => [30000, 200, 10, -32768]
    assertEquals(30000, arr[0]);
    assertEquals(200, arr[1]);
    assertEquals(10, arr[2]);
    assertEquals(-32768, arr[3]);
}

//
// 4. Other Array Methods (copyWithin, fill, indexOf, includes, sort, etc.)
//
function testInt16ArrayOtherMethods() {
    let arr = new Int16Array([1, 2, 3, 4, 5]);

    // A. copyWithin(target, start, end)
    arr.copyWithin(1, 2, 4);  
    // => [1, 3, 4, 4, 5]
    assertEquals(1, arr[0]);
    assertEquals(3, arr[1]);
    assertEquals(4, arr[2]);
    assertEquals(4, arr[3]);
    assertEquals(5, arr[4]);

    // B. fill
    let arr2 = new Int16Array(5);
    arr2.fill(-9999, 1, 4);
    // => [0, -9999, -9999, -9999, 0]
    assertEquals(0, arr2[0]);
    assertEquals(-9999, arr2[1]);
    assertEquals(-9999, arr2[2]);
    assertEquals(-9999, arr2[3]);
    assertEquals(0, arr2[4]);

    // C. indexOf, lastIndexOf
    let arr3 = new Int16Array([10, 20, 30, 20, 10]);
    assertEquals(0, arr3.indexOf(10));
    assertEquals(1, arr3.indexOf(20));
    assertEquals(-1, arr3.indexOf(999));
    assertEquals(4, arr3.lastIndexOf(10));
    assertEquals(3, arr3.lastIndexOf(20));

    // D. includes
    assertTrue(arr3.includes(30));
    assertFalse(arr3.includes(999));
    // fromIndex
    assertTrue(arr3.includes(30, 2));
	assertFalse(arr3.includes(30, 3));

    // E. sort (in-place, numeric ascending)
    let arr4 = new Int16Array([100, -2, 30000, -32768]);
    arr4.sort();
    // => [-32768, -2, 100, 30000]
    assertEquals(-32768, arr4[0]);
    assertEquals(-2, arr4[1]);
    assertEquals(100, arr4[2]);
    assertEquals(30000, arr4[3]);
}

//
// 5. Iteration Protocols & Conversion
//
function testInt16ArrayIterationAndConversion() {
    let arr = new Int16Array([11, -22, 33]);

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
    // => "11,-22,33"
    assertTrue(typeof str === "string");
    assertTrue(str.indexOf(",") >= 0);

    let joined = arr.join("-");
    // => "11--22-33"
    assertTrue(typeof joined === "string");
    assertTrue(joined.indexOf("-") >= 0);
}

//
// 6. Error & Edge Cases
//
function testInt16ArrayErrorCases() {
    let arr = new Int16Array(5);

    // A. set out-of-range offset
    assertThrows(RangeError, () => arr.set([1, 2, 3], 4));
    // Only space for 1 item at index 4

    // B. Negative index => undefined
    assertEquals(undefined, arr[-1]);
    // large index => undefined
    assertEquals(undefined, arr[999]);

    // C. subarray with out-of-bounds => clamp indices
    let sub = arr.subarray(2, 10);
    assertEquals(3, sub.length); // indices 2..4 => 3 elements

	// D. Attempt to create from an empty array like
	assertEquals(0, new Int16Array({ a: 1, b: 2 }).length );

	// E. Attempt typed array with detached buffer (engine-specific):
	let buffer = new ArrayBuffer(16);
	buffer.transfer();
	assertThrows(TypeError, () => new Int16Array(buffer));

	// F. Assigning a value whose magnitude exceeds Integer.MAX_VALUE must
	// wrap modulo 2^16, not saturate (see Int8ArrayTest.js for the root cause).
	arr[0] = 0xF7F7F7F7;
	assertEquals(-2057, arr[0]); // low 16 bits of 0xF7F7F7F7 is 0xF7F7 = -2057 signed
}

//
// 7. Run all tests
//
function runInt16ArrayTests() {
    testInt16ArrayConstructor();
    testInt16ArrayBasicMethods();
    testInt16ArrayArrayMethods();
    testInt16ArrayOtherMethods();
    testInt16ArrayIterationAndConversion();
    testInt16ArrayErrorCases();
}

// (Optional) Execute:
runInt16ArrayTests();
