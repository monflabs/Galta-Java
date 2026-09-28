// Function spread call
function g(a,b,c) {
	return a+b+c;
}
const p = [1, 3, 4]

assertEquals(8, g(...p))
assertEquals(13, g(9, ...p))
assertEquals(16, g(9, 6, ...p))



//
// Object methods
//

const o = {
	f1(a,b,c) {
		return a+b+c;
	}
}

assertEquals(8, o.f1(...p))
assertEquals(13, o.f1(9, ...p))
assertEquals(16, o.f1(9, 6, ...p))

