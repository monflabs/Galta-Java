const odds = new Set([1, 3, 5, 7, 9]);
const squares = new Set([1, 4, 9]);
assertEquals( [3,5,7], toArray(odds.difference(squares)));


function toArray(s) {
	const a = [];
	for(const k of s.keys()) {
		a.push(k)
	}
	return a.sort();
}
