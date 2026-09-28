const inventory = [
	{name: 'apples', quantity: 2},
	{name: 'bananas', quantity: 0},
	{name: 'cherries', quantity: 5}
];

assertSame( inventory[2], inventory.findLast( (o) => o.name === 'cherries' ) );

const _this = [];
[10,22].findLast(function (v,i) {
	assertEquals(i==0?10:22,v);
	assertSame(_this,this);
}, _this);


// Tests for Array.prototype.findLast()

// Basic finding behavior
(function testFindLastBasic() {
    const arr = [5, 12, 8, 130, 44];

    const found = arr.findLast(element => element > 10);
    assertEquals(44, found);

    const found2 = arr.findLast(element => element > 100);
    assertEquals(130, found2);

    const found3 = arr.findLast(element => element < 0);
    assertEquals(undefined, found3);
})();

// Check that callback receives correct arguments
(function testFindLastCallbackArguments() {
    const arr = [10];

    arr.findLast((element, index, array) => {
        assertEquals(10, element);
        assertEquals(0, index);
        assertSame(arr, array);
        return false;
    });
})();

// Check if findLast stops at first match from end
(function testFindLastStopsAtFirstMatch() {
    const arr = [1, 2, 3, 4, 5];
    let callCount = 0;

    const result = arr.findLast(element => {
        callCount++;
        return element < 5;
    });

    assertEquals(4, result);
    assertEquals(2, callCount); // Called for 5, 4
})();

// Check that findLast returns undefined if no element matches
(function testFindLastNoMatch() {
    const arr = [1, 3, 5, 7];
    const result = arr.findLast(element => element % 2 === 0);
    assertSame(undefined, result);
})();

// Check sparse arrays: should visit holes
(function testFindLastSparseArray() {
    const arr = [ , , 5]; // holes at index 0 and 1

    let indexesVisited = [];
    arr.findLast((element, index) => {
        indexesVisited.push(index);
        return false;
    });

    assertEquals(3, indexesVisited.length);
    assertEquals(2, indexesVisited[0]);
    assertEquals(1, indexesVisited[1]);
    assertEquals(0, indexesVisited[2]);
})();

// Check that invalid callbacks throw
(function testFindLastInvalidCallback() {
    const arr = [1, 2, 3];

    assertThrows(TypeError, () => arr.findLast(undefined));
    assertThrows(TypeError, () => arr.findLast(null));
    assertThrows(TypeError, () => arr.findLast(42));
    assertThrows(TypeError, () => arr.findLast('string'));
    assertThrows(TypeError, () => arr.findLast({}));
});

// Check that thisArg is passed correctly
(function testFindLastThisArg() {
    const arr = [1];
    const thisArg = {value: 10};

    arr.findLast(function(element) {
        assertSame(thisArg, this);
        return false;
    }, thisArg);
})();

// Check that findLast does not modify the array
(function testFindLastNoMutation() {
    const arr = [1, 2, 3];
    arr.findLast(element => {
        arr[0] = 100;
        arr[1] = 200;
        return false;
    });
    assertEquals(100, arr[0]);
    assertEquals(200, arr[1]);
})();

// Check callback on empty array
(function testFindLastEmptyArray() {
    const arr = [];
    let called = false;
    const result = arr.findLast(() => {
        called = true;
        return true;
    });
    assertFalse(called);
    assertSame(undefined, result);
})();

// Check callback can delete elements
(function testFindLastCallbackDeletesElements() {
    const arr = [1, 2, 3];

    const result = arr.findLast((element, index, array) => {
        if (index === 2) delete array[1];
        return element === undefined;
    });

    assertSame(undefined, result);
})();