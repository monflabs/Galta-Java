const fruits = ["apple", "banana", "cantaloupe", "blueberries", "grapefruit"];

assertEquals( 3, fruits.findIndex(fruit => fruit === "blueberries") );
assertEquals( -1, fruits.findIndex(fruit => fruit === "ananas") );

const _this = [];
[10,22].findIndex(function (v,i) {
	assertEquals(i==0?10:22,v);
	assertSame(_this,this);
}, _this);


// Tests for Array.prototype.findIndex()

// Basic finding behavior
(function testFindIndexBasic() {
    const arr = [5, 12, 8, 130, 44];

    const found = arr.findIndex(element => element > 10);
    assertEquals(1, found);

    const found2 = arr.findIndex(element => element > 100);
    assertEquals(3, found2);

    const found3 = arr.findIndex(element => element < 0);
    assertEquals(-1, found3);
})();

// Check that callback receives correct arguments
(function testFindIndexCallbackArguments() {
    const arr = [10];

    arr.findIndex((element, index, array) => {
        assertEquals(10, element);
        assertEquals(0, index);
        assertSame(arr, array);
        return false;
    });
})();

// Check if findIndex stops at first match
(function testFindIndexStopsAtFirstMatch() {
    const arr = [1, 2, 3, 4, 5];
    let callCount = 0;

    const result = arr.findIndex(element => {
        callCount++;
        return element > 2;
    });

    assertEquals(2, result);
    assertEquals(3, callCount); // Should have called for 1, 2, 3 only
})();

// Check that findIndex returns -1 if no element matches
(function testFindIndexNoMatch() {
    const arr = [1, 3, 5, 7];
    const result = arr.findIndex(element => element % 2 === 0);
    assertEquals(-1, result);
})();

// Check sparse arrays: should visit holes
(function testFindIndexSparseArray() {
    const arr = [ , , 5]; // holes at index 0 and 1

    let indexesVisited = [];
    arr.findIndex((element, index) => {
        indexesVisited.push(index);
        return false;
    });

    assertEquals(3, indexesVisited.length);
    assertEquals(0, indexesVisited[0]);
    assertEquals(1, indexesVisited[1]);
    assertEquals(2, indexesVisited[2]);
})();

// Check that invalid callbacks throw
(function testFindIndexInvalidCallback() {
    const arr = [1, 2, 3];

    assertThrows(TypeError, () => arr.findIndex(undefined));
    assertThrows(TypeError, () => arr.findIndex(null));
    assertThrows(TypeError, () => arr.findIndex(42));
    assertThrows(TypeError, () => arr.findIndex('string'));
    assertThrows(TypeError, () => arr.findIndex({}));
});

// Check that thisArg is passed correctly
(function testFindIndexThisArg() {
    const arr = [1];
    const thisArg = {value: 10};

    arr.findIndex(function(element) {
        assertSame(thisArg, this);
        return false;
    }, thisArg);
})();

// Check that findIndex does not modify the array
(function testFindIndexNoMutation() {
    const arr = [1, 2, 3];
    arr.findIndex(element => {
        arr[0] = 100;
        arr[1] = 200;
        return false;
    });
    assertEquals(100, arr[0]);
    assertEquals(200, arr[1]);
})();

// Check callback on empty array
(function testFindIndexEmptyArray() {
    const arr = [];
    let called = false;
    const result = arr.findIndex(() => {
        called = true;
        return true;
    });
    assertFalse(called);
    assertEquals(-1, result);
})();

// Check callback can delete elements
(function testFindIndexCallbackDeletesElements() {
    const arr = [1, 2, 3];

    const result = arr.findIndex((element, index, array) => {
        if (index === 0) delete array[1];
        return element === undefined;
    });

    assertEquals(1, result);
})();
