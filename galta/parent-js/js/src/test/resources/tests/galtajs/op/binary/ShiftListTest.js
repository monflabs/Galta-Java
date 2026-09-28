// LIST tests
// The extensive combinations are implemented with the '+' operators
// We just do a simple assement that it works with this operator

assertEquals([4,8], [1,2][*] << 2)
assertEquals([4,16], [1,2][*] << [2,3][*])

assertEquals([4,8], [16,32][*] >> 2)
assertEquals([4,4], [16,32][*] >> [2,3][*])
