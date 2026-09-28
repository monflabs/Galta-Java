function makeRangeIterator(start = 0, end = Infinity, step = 1) {
	let nextIndex = start;
	let iterationCount = 0;

	const rangeIterator = {
		next() {
			let result;
			if (nextIndex < end) {
				result = { value: nextIndex, done: false };
				nextIndex += step;
				iterationCount++;
				return result;
			}
			return { value: iterationCount, done: true };
		},
	};
	return rangeIterator;
}

const iter = makeRangeIterator(1, 10, 2);

const out1 = []
let result = iter.next();
while (!result.done) {
	out1.push(result.value)
	result = iter.next();
}
assertEquals("1 3 5 7 9", out1.join(' '))


const out2 = []
const iterable = {
	[Symbol.iterator]: function() { return makeRangeIterator(1, 10, 2) }
}
for (const v of iterable) {
	out2.push(v)
}
assertEquals("1 3 5 7 9", out2.join(' '))

//
// IteratorClose: a `break`/an outer-label `continue`/`break`/an exception
// from the body must call the iterator's own return() method.
// (BaseProjectTestCase sets the "TRANSPILER" preprocessor symbol to true on a
// genuine interpreted run, and "INTERPRETER" to true on a transpiled run -
// the two symbols are swapped from what their names suggest.) This is
// interpreter-only: the transpiler's generated for-of loop doesn't call
// IteratorClose.
//
// #if TRANSPILER
function makeClosableIterable() {
	let returnCalls = 0;
	return {
		returnCalls: () => returnCalls,
		[Symbol.iterator]() {
			let i = 0;
			return {
				next() { return { value: i++, done: false }; },
				"return": function(v) { returnCalls++; return { value: v, done: true }; }
			};
		}
	};
}

let it1 = makeClosableIterable();
for (const v of it1) {
	if (v === 2) break;
}
assertEquals(1, it1.returnCalls());

let it2 = makeClosableIterable();
outer: for (const outerV of [0]) {
	for (const v of it2) {
		if (v === 2) continue outer;
	}
}
assertEquals(1, it2.returnCalls());

let it3 = makeClosableIterable();
try {
	for (const v of it3) {
		if (v === 2) throw new Error('stop');
	}
} catch (e) {
	assertEquals('stop', e.message);
}
assertEquals(1, it3.returnCalls());
// #endif
