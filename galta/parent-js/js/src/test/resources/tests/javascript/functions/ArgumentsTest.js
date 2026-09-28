function sampleFunction() {
	assertEquals( undefined, arguments.callee )

    return arguments;
}

// Call the function with test values
let args = sampleFunction(10, "hello", true);

// Perform assertions
assertEquals(3, args.length);
assertEquals(10, args[0]);
assertEquals('hello', args[1]);
assertEquals(true, args[2]);

// arguments.length is writable and stays in sync with the internal
// array-like size used by indexed access and the array iterator - a
// direct assignment (not just Object.defineProperty) must be observable.
(function(a, b, c) {
	assertEquals(3, arguments.length);
	arguments.length = 4;
	arguments[3] = 5;
	assertEquals(4, arguments.length);
	assertEquals(5, arguments[3]);

	let iterator = arguments[Symbol.iterator]();
	assertEquals(2, iterator.next().value);
	assertEquals(1, iterator.next().value);
	assertEquals(3, iterator.next().value);
	assertEquals(5, iterator.next().value);
	assertEquals(true, iterator.next().done);
})(2, 1, 3);

// Array iterators re-check the array's live length on every next() call
// (not a snapshot taken at creation time) - but once exhausted, they stay
// exhausted even if the array grows again afterward.
(function() {
	let array = [];
	let iterator = array[Symbol.iterator]();

	array.push('a');
	let r1 = iterator.next();
	assertEquals(false, r1.done);
	assertEquals('a', r1.value);

	let r2 = iterator.next();
	assertEquals(true, r2.done);

	array.push('b');
	let r3 = iterator.next();
	assertEquals(true, r3.done); // stays exhausted, doesn't resume
})();
