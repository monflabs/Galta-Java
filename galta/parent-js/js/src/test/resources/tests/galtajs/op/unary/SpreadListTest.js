//
// Sequence
//
const a = [
	{ a: 1, b: "1,2,30" },
	{ a: 2, b: "4,5" },
	{ a: 3, b: "6,7,8" },
]

//
// Simple mapping
//
function f(a) {
	return a+b
}
const sep = [',']
const r = a[*].b.split(...sep)
assertEquals( [['1','2','30'],['4','5'],['6','7','8']] ,r)
