let a = []

a = a.concat(1)
assertEquals([1],a)

a = a.concat(2,3)
assertEquals([1,2,3],a)

a = a.concat()
assertEquals([1,2,3],a)

a = a.concat([5,6])
assertEquals([1,2,3,5,6],a)


const alpha = ['a', 'b', 'c'];
const numeric = [1, 2, 3];
let alphaNumeric = alpha.concat(numeric);
assertEquals(["a", "b", "c", 1, 2, 3],alphaNumeric)

numeric[Symbol.isConcatSpreadable] = false;
alphaNumeric = alpha.concat(numeric);

assertEquals(["a", "b", "c", [1, 2, 3]], alphaNumeric)
