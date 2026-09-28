const odds = new Set([1, 3, 5, 7, 9]);
const primes = new Set([2, 3, 5, 7]);

console.log("union:", [...odds.union(primes)].join(", "));
console.log("intersection:", [...odds.intersection(primes)].join(", "));
console.log("difference (odds - primes):", [...odds.difference(primes)].join(", "));
console.log("symmetricDifference:", [...odds.symmetricDifference(primes)].join(", "));

console.log("isSubsetOf:", new Set([3, 5]).isSubsetOf(odds));
console.log("isSupersetOf:", odds.isSupersetOf(new Set([3, 5])));
console.log("isDisjointFrom:", odds.isDisjointFrom(new Set([2, 4, 6])));
