// LIST tests
// The extensive combinations are implemented with the '+' operators
// We just do a simple assement that it works with this operator

assertEquals([0,2], [10,22][*] % 5)
assertEquals([2,2], [10,22][*] % [4,10][*])
