// Array_toSpliced_test.js

function testArrayToSplicedBasicDelete() {
    const arr = [1, 2, 3, 4, 5];
    const spliced = arr.toSpliced(1, 2); // remove 2 and 3

    assertFalse(spliced === arr);
    assertEquals(3, spliced.length);
    assertEquals(1, spliced[0]);
    assertEquals(4, spliced[1]);
    assertEquals(5, spliced[2]);

    // Original array must stay the same
    assertEquals(1, arr[0]);
    assertEquals(2, arr[1]);
    assertEquals(3, arr[2]);
    assertEquals(4, arr[3]);
    assertEquals(5, arr[4]);
}

function testArrayToSplicedBasicInsert() {
    const arr = [1, 2, 3];
    const spliced = arr.toSpliced(1, 0, 'a', 'b'); // insert at index 1

    assertEquals(5, spliced.length);
    assertEquals(1, spliced[0]);
    assertEquals('a', spliced[1]);
    assertEquals('b', spliced[2]);
    assertEquals(2, spliced[3]);
    assertEquals(3, spliced[4]);

    assertEquals(1, arr[0]);
    assertEquals(2, arr[1]);
    assertEquals(3, arr[2]);
}

function testArrayToSplicedReplace() {
    const arr = [1, 2, 3, 4];
    const spliced = arr.toSpliced(1, 2, 'x', 'y', 'z');

    assertEquals(5, spliced.length);
    assertEquals(1, spliced[0]);
    assertEquals('x', spliced[1]);
    assertEquals('y', spliced[2]);
    assertEquals('z', spliced[3]);
    assertEquals(4, spliced[4]);
}

function testArrayToSplicedDeletePastEnd() {
    const arr = [1, 2];
    const spliced = arr.toSpliced(5, 2); // start > length

    // Should just clone the array
    assertEquals(2, spliced.length);
    assertEquals(1, spliced[0]);
    assertEquals(2, spliced[1]);
}

function testArrayToSplicedNegativeStart() {
    const arr = [10, 20, 30, 40, 50];
    const spliced = arr.toSpliced(-2, 1); // start from 2nd last

    assertEquals(4, spliced.length);
    assertEquals(10, spliced[0]);
    assertEquals(20, spliced[1]);
    assertEquals(30, spliced[2]);
    assertEquals(50, spliced[3]);
}

function testArrayToSplicedNonArrayReceiver() {
    assertThrows(TypeError, () => {
        Array.prototype.toSpliced.call(null, 0, 1);
    });
    assertThrows(TypeError, () => {
        Array.prototype.toSpliced.call(undefined, 0, 1);
    });
}

function testArrayToSplicedSparseArray() {
    const arr = [1, , 3];
    const spliced = arr.toSpliced(1, 1); // remove the hole

    assertEquals(2, spliced.length);
    assertEquals(1, spliced[0]);
    assertEquals(3, spliced[1]);
}

function testArrayToSplicedThisCoercion() {
    const str = "abcd";
    const spliced = Array.prototype.toSpliced.call(str, 1, 2, "X");

    assertEquals(3, spliced.length);
    assertEquals("a", spliced[0]);
    assertEquals("X", spliced[1]);
    assertEquals("d", spliced[2]);
}

function runTests() {
    testArrayToSplicedBasicDelete();
    testArrayToSplicedBasicInsert();
    testArrayToSplicedReplace();
    testArrayToSplicedDeletePastEnd();
    testArrayToSplicedNegativeStart();
    testArrayToSplicedNonArrayReceiver();
    testArrayToSplicedSparseArray();
    testArrayToSplicedThisCoercion();
}

runTests();
