let a = []
assertEquals([],a.reverse())

let a1 = [1]
assertEquals([1],a1.reverse())

let a2 = [1,2]
assertEquals([2,1],a2.reverse())
assertEquals([3, 2, 1],[1, 2, 3].reverse())

Array.prototype[4] = 99
const aa = [10,11,12,,,13];
assertEquals( [ 13, 99, undefined, 12, 11, 10 ], aa.toReversed() )
assertEquals( [ 13, 99, , 12, 11, 10 ], aa.reverse() )
