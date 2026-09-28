let a = []
assertEquals("",a.join())

let a1 = [3]
assertEquals("3",a1.join())

let a2 = [3,8]
assertEquals("3,8",a2.join())

let a3 = [3,9]
assertEquals("3;9",a3.join(";"))
assertEquals("FireAirWater",['Fire', 'Air', 'Water'].join(""))
assertEquals("Wind + Water + Fire",['Wind', 'Water', 'Fire'].join(" + "))

// null and undefined elements become empty strings
assertEquals("a,,c", ['a', null, 'c'].join(','))
assertEquals("a,,c", ['a', undefined, 'c'].join(','))
assertEquals(",", [null, undefined].join(','))

// Nested arrays are flattened via toString
assertEquals("1,2,3", [[1,2], 3].join(','))

// No separator → comma by default
assertEquals("1,2,3", [1,2,3].join())

// Empty separator
assertEquals("123", [1,2,3].join(""))
