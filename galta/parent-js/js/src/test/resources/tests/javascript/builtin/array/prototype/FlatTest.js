const arr1 = [1, 2, [3, 4]];
assertEquals( [1, 2, 3, 4], arr1.flat() );

const arr2 = [1, 2, [3, 4, [5, 6]]];
assertEquals( [1, 2, 3, 4, [5, 6]], arr2.flat() );

const arr3 = [1, 2, [3, 4, [5, 6]]];
assertEquals( [1, 2, 3, 4, 5, 6], arr3.flat(2) );

const arr4 = [1, 2, [3, 4, [5, 6, [7, 8, [9, 10]]]]];
assertEquals( [1, 2, 3, 4, 5, 6, 7, 8, 9, 10], arr4.flat(Infinity) );

