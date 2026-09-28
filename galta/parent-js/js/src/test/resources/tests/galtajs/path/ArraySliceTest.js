const a$ = [
  1, 2, 3, 4, 5, 6 ,7
]


// Read slices
assertEquals(1,a$[0])

assertEquals(null,a$[3:3])
assertEquals([],a$[3:3][])

assertEquals(1,a$[0:1])
assertEquals([1],a$[0:1][])

assertEquals([1,2],a$[0:2])
assertEquals([1,2],a$[0:2][])

assertEquals([1,2,3,4,5,6,7],a$[:])
assertEquals([2,3,4,5,6,7],a$[1:])
assertEquals([1,2,3],a$[:3])

// Slices with end and steps
assertEquals([1,2,3,4,5,6,7],a$[:])
assertEquals([1,2,3,4,5,6,7],a$[::])
assertEquals([1,3,5,7],a$[::2])
assertEquals([2,4,6],a$[1::2])
assertEquals([1,3],a$[:4:2])
assertEquals([2,4],a$[1:5:2])

assertEquals([4,5,6,7],a$[-4:])
assertEquals([1,2,3,4,5],a$[:-2])

assertThrows( RangeError, () => a$[1:2:0]);
assertEquals(7,a$[7:6:-1])
assertEquals([7,6,5,4,3,2,1],a$[-1:0:-1])
assertEquals([7,6,5,4,3,2,1],a$[-1::-1])
assertEquals([7,6,5,4,3,2,1],a$[::-1])



// Update slices
a$[0:2] = 9
assertEquals([9,9,3,4,5,6,7],a$)

const a1$ = []
a1$[0:3] = 2
assertEquals([2,2,2],a1$)
a1$[0:5] = 4
assertEquals([4,4,4,4,4],a1$)
a1$[3:] = 6
assertEquals([4,4,4,6,6],a1$)
a1$[3:-1] = 7
assertEquals([4,4,4,7,6],a1$)

a1$[1:3] *= 2
assertEquals([4,8,8,7,6],a1$)
