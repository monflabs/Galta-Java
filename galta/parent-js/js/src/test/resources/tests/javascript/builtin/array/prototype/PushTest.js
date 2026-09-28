let a = []

a.push();
assertEquals([],a)

a.push(1);
assertEquals([1],a)

a.push(3,4);
assertEquals([1,3,4],a)

a.push([5,6]);
assertEquals([1,3,4,[5,6]],a)
