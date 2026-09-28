// LIST tests
// The extensive combinations are implemented with the '+' operators
// We just do a simple assement that it works with this operator

assertEquals([2,6], [4,8][*] - 2)
assertEquals([2,5], [4,8][*] - [2,3][*])
