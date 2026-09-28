// LIST tests
// The extensive combinations are implemented with the '+' operators
// We just do a simple assement that it works with this operator

assertEquals(false, [10,10][*] != 10)
assertEquals(false, [10,20][*] != [10,20][*])

assertEquals(true,  [10,10][*] != 12)
assertEquals(true,  [10,12][*] != 10)
assertEquals(true,  [10,20][*] != [12,20][*])
assertEquals(true,  [10,22][*] != [10,20][*])


assertEquals(false, [10,10][*] *!= 10)
assertEquals(false, [10,20][*] *!= [10,20][*])

assertEquals(true,  [10,10][*] *!= 12)
assertEquals(false, [10,12][*] *!= 10)
assertEquals(false, [10,20][*] *!= [12,20][*])
assertEquals(false, [10,22][*] *!= [10,20][*])
