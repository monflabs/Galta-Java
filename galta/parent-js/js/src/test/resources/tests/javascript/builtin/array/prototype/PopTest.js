let a = []
assertEquals(undefined, a.pop())

let a1 = [5]
assertEquals(5, a1.pop())
assertEquals(0, a1.length)

let a2 = [5, 13]
assertEquals(13, a2.pop())
assertEquals(1, a2.length)

// Returns undefined for empty array (per spec §23.1.3.21)
assertEquals(undefined, [].pop())

// Returns the popped element
const arr = [1, 2, 3]
assertEquals(3, arr.pop())
assertEquals(2, arr.length)
