// LIST tests
// The extensive combinations are implemented with the '+' operators
// We just do a simple assement that it works with this operator

assertEquals(false, [5][*] > 10)
assertEquals(false, [10][*] > 10)
assertEquals(true,  [20][*] > 10)

assertEquals(false, [5,5][*] > 10)
assertEquals(false, [10,10][*] > 10)
assertEquals(true,  [20,20,30][*] > 10)

assertEquals(false, [5,5][*] > [10][*])
assertEquals(false, [10,10][*] > [10][*])
assertEquals(true,  [10,20,30][*] > [10][*])

assertEquals(false, [5,5][*] > [20][*])
assertEquals(false, [10,10][*] > [20][*])
assertEquals(true,  [10,20,30][*] > [20][*])


assertEquals(false, [5][*] *> 10)
assertEquals(false, [10][*] *> 10)
assertEquals(true,  [20][*] *> 10)

assertEquals(false, [5,5][*] *> 10)
assertEquals(false, [10,10][*] *> 10)
assertEquals(true,  [20,20,30][*] *> 10)

assertEquals(false, [5,5][*] *> [10][*])
assertEquals(false, [10,10][*] *> [10][*])
assertEquals(false, [10,20,30][*] *> [10][*])

assertEquals(false, [5,5][*] *> [20][*])
assertEquals(false, [10,10][*] *> [20][*])
assertEquals(false, [10,20,30][*] *> [20][*])
