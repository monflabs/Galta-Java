function * idMaker() {
  yield 1;
  yield 2;
  yield;
  yield 3;
}

const gen = idMaker();

assertEquals(1, gen.next().value);
assertEquals(2, gen.next().value);
assertEquals(undefined, gen.next().value);
assertEquals(3, gen.next().value);
assertEquals(undefined, gen.next().value);

assertParseError( `
	function notAGenerator() {
	  yield 1;
	}
` );


function* numbers() {
  yield 1;
  throw new Error("Tada");
}
const n = numbers();

assertEquals(1, n.next().value);
assertThrows(Error, () => n.next());



// Returned value
function* f() {
  yield 1;
  yield 2;
  return 3;
}

const it = f();

assertEquals( 1, it.next().value )
assertEquals( 2, it.next().value )
assertEquals( 3, it.next().value )
assertEquals( undefined, it.next().value )

const r = [ ...f() ]
assertEquals( [1,2], r )


// yield* delegating to another generator
function* inner() {
  yield 'a';
  yield 'b';
}
function* outer() {
  yield 1;
  yield* inner();
  yield 2;
}
assertEquals( [1,'a','b',2], [...outer()] )


// yield* delegating to an array iterable
function* fromArray() {
  yield* [10, 20, 30];
}
assertEquals( [10,20,30], [...fromArray()] )


// yield* delegating to a string iterable
function* fromString() {
  yield* 'hi';
}
assertEquals( ['h','i'], [...fromString()] )


// yield* chains: multiple delegates in sequence
function* a() { yield 1; yield 2; }
function* b() { yield 3; yield 4; }
function* both() { yield* a(); yield* b(); }
assertEquals( [1,2,3,4], [...both()] )


// assertParseError: yield* outside a generator
assertParseError( `
  function notAGenerator() {
    yield* [1,2,3];
  }
` )


// yield* with whitespace between "yield" and "*" (both forms are valid syntax --
// only an actual line break between them would disqualify the yield* form).
function* spaced() {
  yield * [1, 2, 3];
}
assertEquals( [1,2,3], [...spaced()] )


// Generator.prototype.throw(): caught internally by the generator body.
function* g1() {
  try {
    yield 1;
    yield 2;
  } catch(e) {
    yield 'caught:' + e;
  }
}
const genThrow1 = g1();
assertEquals(1, genThrow1.next().value);
assertEquals('caught:boom', genThrow1.throw('boom').value);
assertEquals(true, genThrow1.next().done);

// Generator.prototype.throw(): uncaught, propagates to the caller and completes
// the generator.
function* g2() {
  yield 1;
}
const genThrow2 = g2();
genThrow2.next();
assertThrows(Error, () => genThrow2.throw(new Error("boom")));
assertEquals(true, genThrow2.next().done);

// Generator.prototype.return(): forces completion, runs finally, and is NOT
// visible to the generator body's own try/catch.
let sawFinally = false;
let sawCatch = false;
function* g3() {
  try {
    yield 1;
    yield 2;
  } catch(e) {
    sawCatch = true;
  } finally {
    sawFinally = true;
  }
}
const genReturn = g3();
genReturn.next();
const returnResult = genReturn.return(42);
assertEquals(42, returnResult.value);
assertEquals(true, returnResult.done);
assertEquals(true, sawFinally);
assertEquals(false, sawCatch);

// yield* delegation: outer .throw()/.return() forward into the inner generator.
// (A variant capturing yield*'s own expression value -- `const v = yield* x` -- is
// covered by tests.javascript.control.GeneratorResumeValueTest, interpreted-mode
// only: the transpiler doesn't yet support yield/yield* used as an expression.)
function* innerDelegate() {
  try {
    yield 'a';
    yield 'b';
  } catch(e) {
    yield 'inner-caught:' + e;
  }
}
function* outerDelegate() {
  yield* innerDelegate();
}
const delegated = outerDelegate();
assertEquals('a', delegated.next().value);
assertEquals('inner-caught:oops', delegated.throw('oops').value);

function* innerDelegate2() {
  yield 'x';
  yield 'y';
}
function* outerDelegate2() {
  yield* innerDelegate2();
}
const delegated2 = outerDelegate2();
delegated2.next();
const delegatedReturn = delegated2.return(99);
assertEquals(99, delegatedReturn.value);
assertEquals(true, delegatedReturn.done);

// Generator functions aren't constructible.
{
	function* g() {}
	let threw = false;
	try { new g(); } catch(e) { threw = e instanceof TypeError; }
	assertEquals(true, threw);
}

// A generator instance's [[Prototype]] is the generator function's own
// "prototype" property (not a fixed shared object).
{
	function* g() {}
	assertEquals(true, Object.getPrototypeOf(g()) === g.prototype);
	assertEquals(true, g() instanceof g);
}

// %GeneratorPrototype%[Symbol.toStringTag] is "Generator".
{
	function* g() {}
	assertEquals("Generator", Object.getPrototypeOf(g())[Symbol.toStringTag]);
}

// After a generator body terminates via an uncaught exception, next()'s
// result `value` is undefined (not a leaked raw Java null).
{
	function* g() { yield 1; throw new Error("boom"); }
	const it = g();
	it.next();
	assertThrows(Error, () => it.next());
	assertEquals(undefined, it.next().value);
	assertEquals(true, it.next().done);
}
