assertEquals( true, [1, 2, 3].includes(2)  );
assertEquals( false, [1, 2, 3].includes(4) );
assertEquals( false, [1, 2, 3].includes(3, 3) );
assertEquals( true, [1, 2, 3].includes(3, -1) );
assertEquals( true, [1, 2, NaN].includes(NaN) );
assertEquals( false, ["1", "2", "3"].includes(3) );
