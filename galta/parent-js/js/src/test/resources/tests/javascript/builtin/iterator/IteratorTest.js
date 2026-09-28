// Check that the iterator can be modified while iterated
var s = new Set;

s.add(1);
const iter1 = s.values();
assertEquals({value: 1, done: false}, iter1.next());

s.add(2);
assertEquals({value: 2, done: false}, iter1.next());

assertEquals({value: undefined, done: true}, iter1.next());
assertEquals({value: undefined, done: true}, iter1.next());

// %Iterator% is an abstract base class: direct construction throws, but
// subclassing works and inherits every Iterator.prototype method.
assertThrows(TypeError, () => new Iterator());

class Range extends Iterator {
	constructor(start, end) {
		super();
		this._cur = start;
		this._end = end;
	}
	next() {
		if (this._cur >= this._end) {
			return { value: undefined, done: true };
		}
		return { value: this._cur++, done: false };
	}
}

// Iterator.prototype methods work on a subclass instance directly - a
// generic receiver, not something Java-Iterator-backed under the hood.
assertEquals([0, 2, 4], [...new Range(0, 5).filter(x => x % 2 === 0)]);
assertEquals([0, 1, 4, 9, 16], [...new Range(0, 5).map(x => x * x)]);
assertEquals([0, 1, 2], [...new Range(0, 5).take(3)]);
assertEquals([3, 4], [...new Range(0, 5).drop(3)]);
assertEquals(10, new Range(0, 5).reduce((a, b) => a + b));
assertEquals(true, new Range(0, 5).some(x => x === 3));
assertEquals(false, new Range(0, 5).every(x => x < 3));
assertEquals(3, new Range(0, 5).find(x => x === 3));
assertEquals([0, 1, 2, 3, 4], new Range(0, 5).toArray());
{
	let seen = [];
	new Range(0, 3).forEach(x => seen.push(x));
	assertEquals([0, 1, 2], seen);
}

// A plain object (not a subclass, not Java-Iterator-backed) with
// Iterator.prototype in its chain works too.
{
	const plain = {
		__proto__: Iterator.prototype,
		_cur: 0,
		next() {
			return this._cur < 3 ? { value: this._cur++, done: false } : { value: undefined, done: true };
		},
	};
	assertEquals([0, 2, 4], [...plain.map(x => x * 2)]);
}

// %Iterator.prototype%[Symbol.iterator]() always returns `this`, even for
// non-object receivers.
assertSame(42, Iterator.prototype[Symbol.iterator].call(42));

// Iterator.from(): wraps a plain iterator-shaped object; passes through a
// real generator unchanged (already Iterator.prototype-descended).
{
	const wrapped = Iterator.from({
		_cur: 0,
		next() {
			return this._cur < 3 ? { value: this._cur++, done: false } : { value: undefined, done: true };
		},
	});
	assertEquals([0, 1, 2], [...wrapped]);

	function* g() { yield 1; yield 2; }
	const gen = g();
	assertSame(gen, Iterator.from(gen));
}

// Iterator.zip/zipKeyed/concat (ES2025 joint iteration / iterator sequencing).
{
	assertEquals([[1, "a"], [2, "b"]], [...Iterator.zip([[1, 2, 3], ["a", "b"]])]);
	assertEquals(
		[[1, "a"], [2, "b"], [3, undefined]],
		[...Iterator.zip([[1, 2, 3], ["a", "b"]], { mode: "longest" })]
	);
	assertThrows(TypeError, () => {
		const it = Iterator.zip([[1, 2, 3], ["a", "b"]], { mode: "strict" });
		it.next(); it.next(); it.next();
	});

	const keyed = [...Iterator.zipKeyed({ a: [1, 2], b: ["x", "y"] })];
	assertEquals(2, keyed.length);
	assertEquals({ a: 1, b: "x" }, keyed[0]);
	assertEquals({ a: 2, b: "y" }, keyed[1]);

	assertEquals([1, 2, "a", "b"], [...Iterator.concat([1, 2], ["a", "b"])]);
}

// return() on a map()/filter()/etc. result closes the underlying source and
// permanently stops the helper (never resumes).
{
	let closed = false;
	const source = {
		__proto__: Iterator.prototype,
		_cur: 0,
		next() {
			return this._cur < 5 ? { value: this._cur++, done: false } : { value: undefined, done: true };
		},
		["return"]: function() {
			closed = true;
			return { value: undefined, done: true };
		},
	};
	const helper = source.map(x => x);
	assertEquals(0, helper.next().value);
	const r = helper.return();
	assertEquals(true, closed);
	assertEquals(true, r.done);
	assertEquals(true, helper.next().done);
}
