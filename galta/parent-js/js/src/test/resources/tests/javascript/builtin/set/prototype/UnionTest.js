const evens = new Set([2, 4, 6, 8]);
const squares = new Set([1, 4, 9]);

assertEquals( [1, 2, 4, 6, 8, 9], toArray(evens.union(squares)));


function toArray(s) {
	const a = [];
	for(const k of s.keys()) {
		a.push(k)
	}
	return a.sort();
}
