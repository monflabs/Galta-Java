let a = []
assertEquals(0,a.unshift());
assertEquals([],a)
assertEquals(1,a.unshift(1));
assertEquals([1],a)
assertEquals(3,a.unshift(3,4));
assertEquals([3,4,1],a)

const a2 = [1, 2, 3]; 
assertEquals(5,a2.unshift(4, 5))
assertEquals([4, 5, 1, 2, 3],a2)
