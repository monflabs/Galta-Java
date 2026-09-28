// Array_toSorted_test.js

function testArrayToSortedBasicBehavior() {
    const arr = [3, 1, 4, 1, 5, 9];
    const sorted = arr.toSorted();

    // Check it returns a *new* array
    assertFalse(sorted === arr);
    assertEquals(arr.length, sorted.length);

    // Check sorted content
    assertEquals(1, sorted[0]);
    assertEquals(1, sorted[1]);
    assertEquals(3, sorted[2]);
    assertEquals(4, sorted[3]);
    assertEquals(5, sorted[4]);
    assertEquals(9, sorted[5]);

    // Original array must be unchanged
    assertEquals(3, arr[0]);
    assertEquals(1, arr[1]);
    assertEquals(4, arr[2]);
    assertEquals(1, arr[3]);
    assertEquals(5, arr[4]);
    assertEquals(9, arr[5]);
}

function testArrayToSortedCustomComparator() {
    const arr = [5, 2, 10, 1];
    const sorted = arr.toSorted((a, b) => b - a);

    // Descending order
    assertEquals(10, sorted[0]);
    assertEquals(5, sorted[1]);
    assertEquals(2, sorted[2]);
    assertEquals(1, sorted[3]);

    // Original must not be mutated
    assertEquals(5, arr[0]);
    assertEquals(2, arr[1]);
    assertEquals(10, arr[2]);
    assertEquals(1, arr[3]);
}

function testArrayToSortedEmptyArray() {
    const arr = [];
    const sorted = arr.toSorted();

    assertTrue(Array.isArray(sorted));
    assertEquals(0, sorted.length);
    assertFalse(sorted === arr);
}

function testArrayToSortedSingleElement() {
    const arr = [42];
    const sorted = arr.toSorted();

    assertEquals(1, sorted.length);
    assertEquals(42, sorted[0]);
    assertFalse(sorted === arr);
}

function testArrayToSortedNonArrayReceiver() {
    assertThrows(TypeError, () => {
        Array.prototype.toSorted.call(null);
    });
    assertThrows(TypeError, () => {
        Array.prototype.toSorted.call(undefined);
    });
}

function testArrayToSortedSparseArray() {
    const arr = [ , , 2, , 1 ]; // Sparse array
    const sorted = arr.toSorted();

    assertEquals(5, sorted.length);
    assertTrue(0 in sorted);
    assertTrue(1 in sorted);
    assertTrue(2 in sorted);
    assertTrue(3 in sorted);
    assertTrue(4 in sorted);

    // sorted[0] and sorted[1] are the non-holes
    assertEquals(1, sorted[0]);
    assertEquals(2, sorted[1]);
}

function testArrayToSortedComparatorExceptions() {
    const arr = [1, 2];

    // comparator throws
    assertThrows(Error, () => {
        arr.toSorted(() => { throw new Error("bad"); });
    });
}

function testArrayToSortedThisCoercion() {
    // String is array-like
    const str = "dcba";
    const sorted = Array.prototype.toSorted.call(str);

    assertTrue(Array.isArray(sorted));
    assertEquals(4, sorted.length);
    assertEquals("a", sorted[0]);
    assertEquals("b", sorted[1]);
    assertEquals("c", sorted[2]);
    assertEquals("d", sorted[3]);
}

function runTests() {
    testArrayToSortedBasicBehavior();
    testArrayToSortedCustomComparator();
    testArrayToSortedEmptyArray();
    testArrayToSortedSingleElement();
    testArrayToSortedNonArrayReceiver();
    testArrayToSortedSparseArray();
    testArrayToSortedComparatorExceptions();
    testArrayToSortedThisCoercion();
}

runTests();
