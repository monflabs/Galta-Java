
// LIST tests
// We try many combinations with the '+' operators
// The other binary operators also use the same code for the permutations
const l0 = []
const l1 = [1]
const l2 = [1,2]
const l3 = [1,2,3]

// Add simple values to sequences
assertEquals(null,l0[*]+1)
assertEquals(2, l1[*] +1)
assertEquals(2, 1+l1[*])
assertEquals([2,3], l2[*] +1)
assertEquals([2,3], 1+ l2[*])
assertEquals([3,4,5], 2+ l3[*] )

// Add array values to sequences
assertEquals(null,l0[*]+[1])
assertEquals("11", l1[*] +[1])
assertEquals(2, 1+l1[*])

assertEquals([2,3], l2[*] +[1][*])
assertEquals([2,3], [1][*]+ l2[*])
assertEquals([3,4,5], [2][*]+ l3[*] )

assertEquals(["11","21"], l2[*] +[1])
assertEquals(["11","12"], [1]+ l2[*])
assertEquals(["21","22","23"], [2]+ l3[*] )

// Add sequences
assertEquals(null,l0[*]+l1[*])
assertEquals(2, l1[*] +l1[*])
assertEquals(2, 1+l1[*])
assertEquals([2,4], l2[*] +l2[*])
assertEquals([2,4], l2[*]+ l2[*])
assertEquals([2,4,6], l3[*]+ l3[*] )

// Add different sizes
assertEquals(["13,4,5","23,4,5"], l2[*] + [3,4,5])
assertEquals(["3,4,51","3,4,52"], [3,4,5] + l2[*])
assertEquals([2,4,5], l2[*] + l3[*])

assertEquals([4,6,7], l2[*] + [3,4,5][*])
assertEquals([4,6,7], [3,4,5][*] + l2[*])
assertEquals([2,4,5], l2[*] + l3[*])


assertEquals([2,5,6,7,8], [1,2,3,4,5][*] + [1,3][*])
assertEquals(["11,3","21,3","31,3","41,3","51,3"], [1,2,3,4,5][*] + [1,3])
assertEquals("1,2,3,4,51,3", [1,2,3,4,5] + [1,3])
