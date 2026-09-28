const fours = new Set([4, 8, 12, 16]);
const evens = new Set([2, 4, 6, 8, 10, 12, 14, 16, 18]);
assertTrue(fours.isSubsetOf(evens));


const primes = new Set([2, 3, 5, 7, 11, 13, 17, 19]);
const odds = new Set([3, 5, 7, 9, 11, 13, 15, 17, 19]);
assertFalse(primes.isSubsetOf(odds));

const set1 = new Set([1, 2, 3]);
const set2 = new Set([1, 2, 3]);
assertTrue(set1.isSubsetOf(set2)); 
assertTrue(set2.isSubsetOf(set1)); 
