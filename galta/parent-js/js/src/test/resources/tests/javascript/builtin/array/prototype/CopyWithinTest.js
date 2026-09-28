assertEquals( [1, 2, 3, 1, 2], [1, 2, 3, 4, 5].copyWithin(-2) );
assertEquals( [4, 5, 3, 4, 5], [1, 2, 3, 4, 5].copyWithin(0, 3) );
assertEquals( [4, 2, 3, 4, 5], [1, 2, 3, 4, 5].copyWithin(0, 3, 4) );
assertEquals( [1, 2, 3, 3, 4], [1, 2, 3, 4, 5].copyWithin(3, 2, 4) );
assertEquals( [1, 2, 3, 3, 4], [1, 2, 3, 4, 5].copyWithin(-2, -3, -1) );
