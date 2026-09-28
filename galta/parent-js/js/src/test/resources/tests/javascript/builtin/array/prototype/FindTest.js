const inventory = [
	{name: 'apples', quantity: 2},
	{name: 'bananas', quantity: 0},
	{name: 'cherries', quantity: 5}
];

assertSame( inventory[2], inventory.find( (o) => o.name === 'cherries' ) );

const _this = [];
[10,22].find(function (v,i) {
	assertEquals(i==0?10:22,v);
	assertSame(_this,this);
}, _this);


// Tests for Array.prototype.find()

// Basic finding behavior
(function testFindBasic() {
    const arr = [5, 12, 8, 130, 44];

    const found = arr.find(element => element > 10);
    assertEquals(12, found);

    const found2 = arr.find(element => element > 100);
    assertEquals(130, found2);

    const found3 = arr.find(element => element < 0);
    assertEquals(undefined, found3);
})();

// Check that callback receives correct arguments
(function testFindCallbackArguments() {
    const arr = [10];

    arr.find((element, index, array) => {
        assertEquals(10, element);
        assertEquals(0, index);
        assertSame(arr, array);
        return false;
    });
})();

// Check if find stops at first match
(function testFindStopsAtFirstMatch() {
    const arr = [1, 2, 3, 4, 5];
    let callCount = 0;

    const result = arr.find(element => {
        callCount++;
        return element > 2;
    });

    assertEquals(3, result);
    assertEquals(3, callCount); // Should have called for 1, 2, 3 only
})();

// Check that find returns undefined if no element matches
(function testFindNoMatch() {
    const arr = [1, 3, 5, 7];
    const result = arr.find(element => element % 2 === 0);
    assertSame(undefined, result);
})();

// Check sparse arrays: should visit holes
(function testFindSparseArray() {
    const arr = [ , , 5]; // holes at index 0 and 1

    let indexesVisited = [];
    arr.find((element, index) => {
        indexesVisited.push(index);
        return false;
    });

    assertEquals(3, indexesVisited.length);
    assertEquals(0, indexesVisited[0]);
    assertEquals(1, indexesVisited[1]);
    assertEquals(2, indexesVisited[2]);
})();

// Check that invalid callbacks throw
(function testFindInvalidCallback() {
    const arr = [1, 2, 3];

    assertThrows(TypeError, () => arr.find(undefined));
    assertThrows(TypeError, () => arr.find(null));
    assertThrows(TypeError, () => arr.find(42));
    assertThrows(TypeError, () => arr.find('string'));
    assertThrows(TypeError, () => arr.find({}));
});

// Check that thisArg is passed correctly
(function testFindThisArg() {
    const arr = [1];
    const thisArg = {value: 10};

    arr.find(function(element) {
        assertSame(thisArg, this);
        return false;
    }, thisArg);
})();

// Check that find does not modify the array
(function testFindNoMutation() {
    const arr = [1, 2, 3];
    arr.find(element => {
        arr[0] = 100;
        arr[1] = 200;
        return false;
    });
    assertEquals(100, arr[0]);
    assertEquals(200, arr[1]);
});

// Check callback on empty array
(function testFindEmptyArray() {
    const arr = [];
    let called = false;
    const result = arr.find(() => {
        called = true;
        return true;
    });
    assertFalse(called);
    assertSame(undefined, result);
})();

// Check callback can delete elements
(function testFindCallbackDeletesElements() {
    const arr = [1, 2, 3];

    const result = arr.find((element, index, array) => {
        if (index === 0) delete array[1];
        return element === undefined;
    });

    assertSame(undefined, result);
})();
