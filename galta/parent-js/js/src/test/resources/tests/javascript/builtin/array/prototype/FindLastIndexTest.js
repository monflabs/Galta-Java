const fruits = ["apple", "banana", "cantaloupe", "blueberries", "grapefruit"];

assertEquals( 3, fruits.findLastIndex(fruit => fruit === "blueberries") );
assertEquals( -1, fruits.findLastIndex(fruit => fruit === "ananas") );

const _this = [];
[10,22].findLastIndex(function (v,i) {
	assertEquals(i==0?10:22,v);
	assertSame(_this,this);
}, _this);

// Tests for Array.prototype.findLastIndex()

// Basic finding behavior
(function testFindLastIndexBasic() {
    const arr = [5, 12, 8, 130, 44];

    const found = arr.findLastIndex(element => element > 10);
    assertEquals(4, found);

    const found2 = arr.findLastIndex(element => element > 100);
    assertEquals(3, found2);

    const found3 = arr.findLastIndex(element => element < 0);
    assertEquals(-1, found3);
})();

// Check that callback receives correct arguments
(function testFindLastIndexCallbackArguments() {
    const arr = [10];

    arr.findLastIndex((element, index, array) => {
        assertEquals(10, element);
        assertEquals(0, index);
        assertSame(arr, array);
        return false;
    });
})();

// Check if findLastIndex stops at first match from end
(function testFindLastIndexStopsAtFirstMatch() {
    const arr = [1, 2, 3, 4, 5];
    let callCount = 0;

    const result = arr.findLastIndex(element => {
        callCount++;
        return element < 5;
    });

    assertEquals(3, result);
    assertEquals(2, callCount); // Called for 5, 4
})();

// Check that findLastIndex returns -1 if no element matches
(function testFindLastIndexNoMatch() {
    const arr = [1, 3, 5, 7];
    const result = arr.findLastIndex(element => element % 2 === 0);
    assertEquals(-1, result);
})();

// Check sparse arrays: should visit holes
(function testFindLastIndexSparseArray() {
    const arr = [ , , 5]; // holes at index 0 and 1

    let indexesVisited = [];
    arr.findLastIndex((element, index) => {
        indexesVisited.push(index);
        return false;
    });

    assertEquals(3, indexesVisited.length);
    assertEquals(2, indexesVisited[0]);
    assertEquals(1, indexesVisited[1]);
    assertEquals(0, indexesVisited[2]);
})();

// Check that invalid callbacks throw
(function testFindLastIndexInvalidCallback() {
    const arr = [1, 2, 3];

    assertThrows(TypeError, () => arr.findLastIndex(undefined));
    assertThrows(TypeError, () => arr.findLastIndex(null));
    assertThrows(TypeError, () => arr.findLastIndex(42));
    assertThrows(TypeError, () => arr.findLastIndex('string'));
    assertThrows(TypeError, () => arr.findLastIndex({}));
})();

// Check that thisArg is passed correctly
(function testFindLastIndexThisArg() {
    const arr = [1];
    const thisArg = {value: 10};

    arr.findLastIndex(function(element) {
        assertSame(thisArg, this);
        return false;
    }, thisArg);
})();

// Check that findLastIndex does not modify the array
(function testFindLastIndexNoMutation() {
    const arr = [1, 2, 3];
    arr.findLastIndex(element => {
        arr[0] = 100;
        arr[1] = 200;
        return false;
    });
    assertEquals(100, arr[0]);
    assertEquals(200, arr[1]);
})();

// Check callback on empty array
(function testFindLastIndexEmptyArray() {
    const arr = [];
    let called = false;
    const result = arr.findLastIndex(() => {
        called = true;
        return true;
    });
    assertFalse(called);
    assertEquals(-1, result);
})();

// Check callback can delete elements
(function testFindLastIndexCallbackDeletesElements() {
    const arr = [1, 2, 3];

    const result = arr.findLastIndex((element, index, array) => {
        if (index === 2) delete array[1];
        return element === undefined;
    });

    assertEquals(1, result);
})();
