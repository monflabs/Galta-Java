// Array_toReversed_test.js

function testArrayToReversedBasicBehavior() {
    const arr = [1, 2, 3, 4, 5];
    const reversed = arr.toReversed();

    // Check it returns a *new* array
    assertFalse(reversed === arr);
    assertEquals(arr.length, reversed.length);

    // Check reversed content
    assertEquals(5, reversed[0]);
    assertEquals(4, reversed[1]);
    assertEquals(3, reversed[2]);
    assertEquals(2, reversed[3]);
    assertEquals(1, reversed[4]);

    // Original array must be unchanged
    assertEquals(1, arr[0]);
    assertEquals(2, arr[1]);
    assertEquals(3, arr[2]);
    assertEquals(4, arr[3]);
    assertEquals(5, arr[4]);
}

function testArrayToReversedEmptyArray() {
    const arr = [];
    const reversed = arr.toReversed();

    assertTrue(Array.isArray(reversed));
    assertEquals(0, reversed.length);
    assertFalse(reversed === arr);
}

function testArrayToReversedSingleElement() {
    const arr = [42];
    const reversed = arr.toReversed();

    assertEquals(1, reversed.length);
    assertEquals(42, reversed[0]);
    assertFalse(reversed === arr);
}

function testArrayToReversedNonArrayReceiver() {
    assertThrows(TypeError, () => {
        Array.prototype.toReversed.call(null);
    });
    assertThrows(TypeError, () => {
        Array.prototype.toReversed.call(undefined);
    });
}

function testArrayToReversedSparseArray() {
    const arr = [ , , 1, 2 ]; // Sparse array
    const reversed = arr.toReversed();

    assertEquals(4, reversed.length);

    // Check sparsity is preserved correctly after reversal
    assertEquals(2, reversed[0]);
    assertEquals(1, reversed[1]);
    assertTrue(2 in reversed);
    assertTrue(3 in reversed);
}

function testArrayToReversedThisCoercion() {
    // Strings are array-like
    const str = "abcd";
    const reversed = Array.prototype.toReversed.call(str);

    assertTrue(Array.isArray(reversed));
    assertEquals(4, reversed.length);
    assertEquals("d", reversed[0]);
    assertEquals("c", reversed[1]);
    assertEquals("b", reversed[2]);
    assertEquals("a", reversed[3]);
}

function runTests() {
    testArrayToReversedBasicBehavior();
    testArrayToReversedEmptyArray();
    testArrayToReversedSingleElement();
    testArrayToReversedNonArrayReceiver();
    testArrayToReversedSparseArray();
    testArrayToReversedThisCoercion();
}

runTests();
