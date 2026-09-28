// LIST tests
// The extensive combinations are implemented with the '+' operators
// We just do a simple assement that it works with this operator

assertEquals([0,2], [4,6][*] & 2)
assertEquals([0,4], [4,6][*] & [2,4][*])
