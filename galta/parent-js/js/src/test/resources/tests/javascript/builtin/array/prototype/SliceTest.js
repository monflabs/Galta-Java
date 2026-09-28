let a = [1,2,3,4,5,6]

assertEquals([1,2,3,4,5,6],a.slice())
assertEquals([1,2,3,4,5,6],a.slice(0))
assertEquals([2,3,4,5,6],a.slice(1))
assertEquals([2],a.slice(1,2))
assertEquals([2,3],a.slice(1,3))
assertEquals([5,6],a.slice(-2))
assertEquals([4],a.slice(-3,-2))
assertEquals([4,5],a.slice(-3,-1))
assertEquals([],a.slice(1,0))
assertEquals([2,3,4,5],a.slice(1,-1))
