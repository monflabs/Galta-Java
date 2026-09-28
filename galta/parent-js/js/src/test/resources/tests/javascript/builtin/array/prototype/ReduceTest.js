const getMax = (a, b) => a<b ? b : a;

// callback is invoked for each element in the array starting at index 0
assertEquals(100,[1, 100].reduce(getMax, 50) );
assertEquals(50, [    50].reduce(getMax, 10) );

// callback is invoked once for element at index 1
assertEquals(100,[1, 100].reduce(getMax));

// callback is not invoked
assertEquals(50,[    50].reduce(getMax));
assertEquals(1, [      ].reduce(getMax, 1));

// Empty array without initialValue → TypeError
assertThrows(TypeError, () => [].reduce(getMax));

// Callback receives (accumulator, currentValue, currentIndex, array)
const arr = [10, 20, 30];
const indices = [];
arr.reduce((acc, val, idx, src) => {
    indices.push(idx);
    assertSame(arr, src);
    return acc + val;
}, 0);
assertEquals([0, 1, 2], indices);

// String concatenation left-to-right
assertEquals('abc', ['a','b','c'].reduce((acc, v) => acc + v, ''));

// No initialValue: first element is accumulator, iteration starts at index 1
let startIdx = null;
[10, 20, 30].reduce((acc, val, idx) => {
    if (startIdx === null) startIdx = idx;
    return acc + val;
});
assertEquals(1, startIdx); // first callback call is at index 1

// Single element, no initialValue: returns element, callback not called
let called = false;
assertEquals(42, [42].reduce(() => { called = true; return 0; }));
assertFalse(called);
