// Basic sort — sorts in-place and returns same array reference
let a = [5,1,4,3,6,2]
const ref = a
a.sort()
assertSame(ref, a)
assertEquals([1,2,3,4,5,6], a)

// Default sort converts to strings: "10" < "9" lexicographically
const numStr = [10, 9, 2, 1, 100]
numStr.sort()
assertEquals([1, 10, 100, 2, 9], numStr)

// Custom numeric comparator
assertEquals([1,2,3,4,5,6], [5,1,4,3,6,2].sort((a1,a2) => a1-a2))
assertEquals([6,5,4,3,2,1], [1,2,3,4,5,6].sort((a1,a2) => a2-a1))

// Empty and single element
assertEquals([], [].sort())
assertEquals([42], [42].sort())

// Already sorted stays sorted
assertEquals([1,2,3], [1,2,3].sort((a,b) => a-b))

// Stable sort: elements that compare equal preserve original relative order
const items = [
    {k: 'b', i: 0},
    {k: 'a', i: 1},
    {k: 'b', i: 2},
    {k: 'a', i: 3},
]
items.sort((x,y) => x.k < y.k ? -1 : x.k > y.k ? 1 : 0)
assertEquals('a', items[0].k)
assertEquals(1, items[0].i)   // first 'a' stays before second 'a'
assertEquals('a', items[1].k)
assertEquals(3, items[1].i)
assertEquals('b', items[2].k)
assertEquals(0, items[2].i)   // first 'b' stays before second 'b'
assertEquals('b', items[3].k)
assertEquals(2, items[3].i)

// undefined values sort to the end regardless of comparator
const withUndef = [3, undefined, 1, undefined, 2]
withUndef.sort((a,b) => a-b)
assertEquals(1, withUndef[0])
assertEquals(2, withUndef[1])
assertEquals(3, withUndef[2])
assertEquals(undefined, withUndef[3])
assertEquals(undefined, withUndef[4])

// undefined sorts to end with default (string) comparator too
const withUndef2 = ['b', undefined, 'a']
withUndef2.sort()
assertEquals('a', withUndef2[0])
assertEquals('b', withUndef2[1])
assertEquals(undefined, withUndef2[2])

// Comparator returning NaN is treated as +0 (no swap)
const nanComp = [1, 2, 3]
nanComp.sort(() => NaN)
assertEquals(3, nanComp.length)

// String comparison: "banana" before "cherry"
assertEquals(['apple','banana','cherry'], ['cherry','apple','banana'].sort())

// Negative zero and zero are equal
assertEquals([0, -0, 0].sort((a,b)=>a-b).length, 3)
