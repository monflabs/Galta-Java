//
// 1. Constructor Tests and Basic Properties
//
function testInt32ArrayConstructor() {
	let arr0 = new Int32Array();
	assertEquals(0, arr0.length);
	assertEquals('[object Int32Array]', Object.prototype.toString.call(arr0));

	// A. Construct with length
    let arr1 = new Int32Array(5);
    assertEquals(5, arr1.length);
    // All elements should be 0 by default
    for (let i = 0; i < arr1.length; i++) {
        assertEquals(0, arr1[i]);
    }

    // BYTES_PER_ELEMENT should be 4 for 32-bit signed
    assertEquals(4, Int32Array.BYTES_PER_ELEMENT);
	assertEquals(4, Int32Array.prototype.BYTES_PER_ELEMENT);
	assertEquals(4, arr0.BYTES_PER_ELEMENT);

    // B. Construct from array-like
    let arr2 = new Int32Array([10, -20, 3000000000]);
	assertEquals(4, arr2.BYTES_PER_ELEMENT);
    assertEquals(3, arr2.length);
    assertEquals(10, arr2[0]);
    assertEquals(-20, arr2[1]);
    // 3000000000 mod 2^32 => 3000000000 - 4294967296 => -1294967296 in 32-bit signed
    // So it should wrap around to: 3000000000 >>> 0 = 70, intended => 
    // Let's calculate:
    //   3000000000 = 0xB2D05E00 => as a signed 32-bit, that is -1294967296
    // We'll check the actual stored value:
    assertEquals(-1294967296, arr2[2]);

    // C. Construct from another typed array
    let arr3 = new Int32Array(arr2);
    assertEquals(3, arr3.length);
    assertEquals(10, arr3[0]);
    assertEquals(-20, arr3[1]);
    assertEquals(-1294967296, arr3[2]);
    // Changing arr2 does not affect arr3
    arr2[0] = 9999;
    assertEquals(10, arr3[0]);

    // D. Construct from ArrayBuffer
    //   16 bytes => can hold 4 Int32 elements
    let buffer = new ArrayBuffer(16);
    let arr4 = new Int32Array(buffer);
    assertEquals(4, arr4.length);
    assertSame(buffer, arr4.buffer);
    assertEquals(0, arr4.byteOffset);
    assertEquals(16, arr4.byteLength);

    // E. Construct from ArrayBuffer with offset and length
    //   offset=8 => length=2 => 2 * 4 = 8 bytes from offset=8 => total 16 bytes => fits exactly
    let arr5 = new Int32Array(buffer, 8, 2);
    assertEquals(2, arr5.length);
    assertSame(buffer, arr5.buffer);
    assertEquals(8, arr5.byteOffset);
    assertEquals(8, arr5.byteLength);

    // F. Invalid constructor arguments
    // Negative length
    assertThrows(RangeError, () => new Int32Array(-1));
    // Offset out of range
    assertThrows(RangeError, () => new Int32Array(buffer, 17));
    // Offset + length out of range
    assertThrows(RangeError, () => new Int32Array(buffer, 0, 5)); // 5*4=20 > 16
	// Non-integer length
	assertEquals(2, new Int32Array(2.5).length);
	assertEquals(2, new Int32Array("2").length);
	assertEquals(0, new Int32Array(null).length);
	assertEquals(0, new Int32Array(undefined).length);
}

//
// 2. Basic Array Operations (index assignment, set, subarray, slice, etc.)
//
function testInt32ArrayBasicMethods() {
    // A. Basic index assignment with wrap-around for out-of-range 32-bit
    let arr = new Int32Array(3);
    arr[0] = 2147483647;  // max 32-bit signed
    arr[1] = 2147483648;  // => -2147483648 (overflow)
    arr[2] = -2147483649; // => 2147483647 (underflow)
    assertEquals(2147483647, arr[0]);
    assertEquals(-2147483648, arr[1]);
    assertEquals(2147483647, arr[2]);

    // B. set (from array-like)
    let arr2 = new Int32Array(4);
    arr2.set([10, -20, 3000000000, 2147483648]);  
    // 3000000000 => -1294967296
    // 2147483648 => -2147483648
    assertEquals(10, arr2[0]);
    assertEquals(-20, arr2[1]);
    assertEquals(-1294967296, arr2[2]);
    assertEquals(-2147483648, arr2[3]);

    // C. Overlapping set
    let arr3 = new Int32Array([1, 2, 3, 4]);
    arr3.set(arr3.subarray(1, 3), 0);
    // copy [2,3] to arr3[0..1] => [2,3,3,4]
    assertEquals(2, arr3[0]);
    assertEquals(3, arr3[1]);
    assertEquals(3, arr3[2]);
    assertEquals(4, arr3[3]);

    // D. subarray (same buffer, no copy)
    let arr4 = new Int32Array([10, 20, 30, 40]);
    let sub = arr4.subarray(1, 3); // => [20, 30]
    assertEquals(2, sub.length);
    assertEquals(20, sub[0]);
    // changes in sub reflect in arr4
    sub[1] = -999999999; 
    // -999999999 mod 2^32 => e.g. 2, still negative. 
    // Let's just check the stored value is indeed -999999999:
    assertEquals(-999999999, arr4[2]);

    // E. slice (creates a copy, if implemented)
    if (typeof arr4.slice === "function") {
        let sliceResult = arr4.slice(1, 3);
        // => new Int32Array [20, -999999999]
        assertEquals(2, sliceResult.length);
        assertEquals(20, sliceResult[0]);
        // changes in sliceResult do not affect arr4
        sliceResult[0] = 999999999;
        assertEquals(20, arr4[1]);
    }
}

//
// 3. Higher-Order Array Methods (map, filter, reduce, etc.)
//
function testInt32ArrayArrayMethods() {
    let arr = new Int32Array([-2147483648, 10, 200, 2000000000]);
    // -2147483648 => min 32-bit int
    // 2000000000 => well within range

    // A. forEach
    let sum = 0;
    arr.forEach(val => sum += val);
    // => -2147483648 + 10 + 200 + 2000000000 => -2147483648 + 2000000210 => -147483437
    assertEquals(-147483438, sum);

    // B. map => new Int32Array
    let mapped = arr.map(x => x + 1);
    // => [-2147483647, 11, 201, 2000000001]
    // watch for overflow if it was at limit
    assertEquals(-2147483647, mapped[0]);
    assertEquals(11, mapped[1]);
    assertEquals(201, mapped[2]);
    assertEquals(2000000001, mapped[3]);

    // C. filter => new Int32Array
    let filtered = arr.filter(x => x > 0);
    // => [10, 200, 2000000000]
    assertEquals(3, filtered.length);
    assertEquals(10, filtered[0]);
    assertEquals(200, filtered[1]);
    assertEquals(2000000000, filtered[2]);

    // D. reduce
    let product = arr.reduce((acc, val) => acc * val, 1);
    // => 1 * (-2147483648) * 10 * 200 * 2000000000 => huge number => JS numeric overflow => Infinity or a big negative
    // The typed array doesn't limit the reduce result, so let's see:
    // => -2147483648 * 10 = -21474836480
    // => * 200 = -4294967296000
    // => * 2000000000 = -8.589934592e+18
    // We'll just do a basic approximate check:
    assertTrue(product < -1.0e18);

    // E. some, every
    assertTrue(arr.some(x => x === 10));
    assertFalse(arr.some(x => x === 999999999));
    assertFalse(arr.every(x => x > 0));
    assertTrue(arr.every(x => Number.isInteger(x))); // all integers

    // F. find, findIndex
    let found = arr.find(x => x > 1000);
    assertEquals(2000000000, found);
    let foundIndex = arr.findIndex(x => x > 1000);
    assertEquals(3, foundIndex);

    // G. reverse (in-place)
    // arr => [-2147483648, 10, 200, 2000000000]
    let reversed = arr.reverse();
    assertSame(arr, reversed);
    // => [2000000000, 200, 10, -2147483648]
    assertEquals(2000000000, arr[0]);
    assertEquals(200, arr[1]);
    assertEquals(10, arr[2]);
    assertEquals(-2147483648, arr[3]);
}

//
// 4. Other Array Methods (copyWithin, fill, indexOf, includes, sort, etc.)
//
function testInt32ArrayOtherMethods() {
    let arr = new Int32Array([1, 2, 3, 4, 5]);

    // A. copyWithin
    arr.copyWithin(1, 2, 4);  
    // => [1, 3, 4, 4, 5]
    assertEquals(1, arr[0]);
    assertEquals(3, arr[1]);
    assertEquals(4, arr[2]);
    assertEquals(4, arr[3]);
    assertEquals(5, arr[4]);

    // B. fill
    let arr2 = new Int32Array(5);
    arr2.fill(-999999999, 1, 4); 
    // large negative => mod 2^32 => let's check direct
    // -999999999 =>  -999999999 is still within 32-bit => about  -999999999
    // should store exactly that if it fits the 32-bit range. It does => ~ -9.99999999e8
    // => [0, -999999999, -999999999, -999999999, 0]
    assertEquals(0, arr2[0]);
    assertEquals(-999999999, arr2[1]);
    assertEquals(-999999999, arr2[2]);
    assertEquals(-999999999, arr2[3]);
    assertEquals(0, arr2[4]);

    // C. indexOf, lastIndexOf
    let arr3 = new Int32Array([10, 20, 30, 20, 10]);
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
	assertFalse(arr3.includes(30, 23));

    // E. sort (in-place, numeric ascending)
    let arr4 = new Int32Array([100, -2, 2147483647, 10]);
    arr4.sort();
    // => [-2, 10, 100, 2147483647]
    assertEquals(-2, arr4[0]);
    assertEquals(10, arr4[1]);
    assertEquals(100, arr4[2]);
    assertEquals(2147483647, arr4[3]);
}

//
// 5. Iteration Protocols & Conversion
//
function testInt32ArrayIterationAndConversion() {
    let arr = new Int32Array([11, -22, 33]);

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
function testInt32ArrayErrorCases() {
    let arr = new Int32Array(5);

    // A. set: out-of-range offset
    assertThrows(RangeError, () => arr.set([1, 2, 3], 4));
    // Only space for 1 element at index 4

    // B. Negative index => undefined
    assertEquals(undefined, arr[-1]);
    // large index => undefined
    assertEquals(undefined, arr[999]);

    // C. subarray with out-of-bounds => clamps
    let sub = arr.subarray(3, 10);
    assertEquals(2, sub.length); // from index 3..4 => 2 elements

    // D. Attempt to create from an empty array like
    assertEquals(0, new Int32Array({ a: 1, b: 2 }).length );

    // E. Attempt typed array with detached buffer (engine-specific):
    let buffer = new ArrayBuffer(16);
    buffer.transfer();
    assertThrows(TypeError, () => new Int32Array(buffer));
}

//
// 7. Run all tests
//
function runInt32ArrayTests() {
    testInt32ArrayConstructor();
    testInt32ArrayBasicMethods();
    testInt32ArrayArrayMethods();
    testInt32ArrayOtherMethods();
    testInt32ArrayIterationAndConversion();
    testInt32ArrayErrorCases();
}

// (Optional) Execute immediately:
runInt32ArrayTests();
