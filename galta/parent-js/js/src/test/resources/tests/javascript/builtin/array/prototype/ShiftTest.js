let a = []
assertEquals(undefined, a.shift())
assertEquals([], a)

let a1 = [3]
assertEquals(3, a1.shift())
assertEquals([], a1)

let a2 = [3, 8]
assertEquals(3, a2.shift())
assertEquals([8], a2)

// Returns undefined for empty array (per spec §23.1.3.33)
assertEquals(undefined, [].shift())
