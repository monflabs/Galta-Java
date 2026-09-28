// Tests for Array.prototype.with

function testArrayWithBasicReplacement() {
    const arr = [1, 2, 3];
    const newArr = arr.with(1, 99);
    assertEquals(1, newArr[0]);
    assertEquals(99, newArr[1]);
    assertEquals(3, newArr[2]);
    assertEquals(3, newArr.length);
    assertFalse(arr === newArr); // must create a new array
}

function testArrayWithNegativeIndex() {
    const arr = ['a', 'b', 'c'];
    const newArr = arr.with(-1, 'z');
    assertEquals('a', newArr[0]);
    assertEquals('b', newArr[1]);
    assertEquals('z', newArr[2]);
}

function testArrayWithOutOfBoundsPositive() {
    const arr = [10, 20, 30];
    assertThrows(RangeError, () => arr.with(3, 40)); // index too large
    assertThrows(RangeError, () => arr.with(5, 50));
}

function testArrayWithOutOfBoundsNegative() {
    const arr = [10, 20, 30];
    assertThrows(RangeError, () => arr.with(-4, 0)); // index too small
    assertThrows(RangeError, () => arr.with(-100, 0));
}

function testArrayWithOriginalArrayUnchanged() {
    const arr = [1, 2, 3];
    arr.with(0, 9);
    assertEquals(1, arr[0]);
    assertEquals(2, arr[1]);
    assertEquals(3, arr[2]);
}

function testArrayWithSparseArray() {
    const arr = [1, , 3]; // sparse array with a hole
    const newArr = arr.with(1, 2);
    assertEquals(1, newArr[0]);
    assertEquals(2, newArr[1]);
    assertEquals(3, newArr[2]);
    assertTrue(1 in newArr); // index 1 must exist now
}

function testArrayWithObjectCoercion() {
    const obj = { length: 2, 0: 'a', 1: 'b', with: Array.prototype.with };
    const newObj = obj.with(0, 'z');
    assertEquals('z', newObj[0]);
    assertEquals('b', newObj[1]);
    assertEquals(2, newObj.length);
}

function testArrayWithUndefinedValue() {
    const arr = [1, 2, 3];
    const newArr = arr.with(1, undefined);
    assertEquals(undefined, newArr[1]);
}

function testArrayWithNullValue() {
    const arr = [1, 2, 3];
    const newArr = arr.with(1, null);
    assertEquals(null, newArr[1]);
}

// Run all tests
testArrayWithBasicReplacement();
testArrayWithNegativeIndex();
testArrayWithOutOfBoundsPositive();
testArrayWithOutOfBoundsNegative();
testArrayWithOriginalArrayUnchanged();
testArrayWithSparseArray();
testArrayWithObjectCoercion();
testArrayWithUndefinedValue();
testArrayWithNullValue();
