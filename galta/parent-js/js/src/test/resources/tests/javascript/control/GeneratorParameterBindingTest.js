// Generator parameter binding (FunctionDeclarationInstantiation: destructuring
// defaults, var hoisting) must happen synchronously at call time, before the
// generator object is returned - only the body's statements are genuinely lazy.

{
  let f = function*([x = (function(){ throw new Error('boom'); })()]) {};
  let threwAtCall = false;
  try { f([]); } catch(e) { threwAtCall = (e.message === 'boom'); }
  assertEquals(true, threwAtCall);
}

{
  let initCount = 0;
  function* g(a = (initCount++, 5)) { yield a; }
  let it = g();
  let boundBeforeNext = (initCount === 1);
  it.next();
  assertEquals(true, boundBeforeNext);
}

// A parameter-less, local-var-free generator has nothing to bind eagerly -
// the codegen split is skipped entirely for it (base no-op
// initGeneratorParams). Sanity check the ordinary lazy body still works.
{
  function* g() { yield 1; yield 2; return 3; }
  let it = g();
  assertEquals(1, it.next().value);
  assertEquals(2, it.next().value);
  assertEquals(3, it.next().value);
}

// A body-scoped nested function (either a `var f = function(){}` expression
// or a hoisted function declaration) alongside a generator's own params keeps
// the split disabled - see ASTFunction's splitForGenerator guard and
// hasBodyScopedFunctions(). The inner function's class body is emitted from
// transpilerDeclareStatement (called by the parameter-binding prologue) while
// its instantiation lives in the body's callVoid; moving only the class into
// initGeneratorParams would make it invisible from callVoid. Documented here
// so a regression (a crash, rather than just deferred binding) would be
// caught. Correctness (the closure captures x) is what these two cases test.
{
  function* g(x) {
    var inner = function() { return x; };
    yield inner();
  }
  assertEquals(42, g(42).next().value);
}
{
  function* g(x) {
    function inner() { return x; }
    yield inner();
  }
  assertEquals(42, g(42).next().value);
}

// A function expression LIVING ENTIRELY INSIDE the parameter subtree (e.g.
// invoked from a parameter default) does NOT block the split: both its class
// emission and its instantiation move together into initGeneratorParams. This
// exercises hasBodyScopedFunctions()'s parameter-subtree exemption.
{
  let calls = 0;
  function* g(a = (function(){ calls++; return 7; })()) { yield a; }
  g();
  assertEquals(1, calls);
}

// The recursive self-reference assignment (a named generator function
// expression referencing its own name) is part of the prologue moved into
// initGeneratorParams - must still be visible from the (lazy) body.
{
  let f = function* fact(n) {
    if(n<=1) { return 1; }
    let it = fact(n-1);
    let r = it.next();
    while(!r.done) { r = it.next(); }
    return n*r.value;
  };
  let it = f(3);
  let r = it.next();
  while(!r.done) { r = it.next(); }
  assertEquals(6, r.value);
}

// Generator methods (class and object literal) route through the exact same
// isGenerator() call path as plain generator functions.
{
  class C { *m({x}) {} }
  let c = new C();
  let threw = false;
  try { c.m(null); } catch(e) { threw = (e instanceof TypeError); }
  assertEquals(true, threw);
}

{
  let o = { *m({x}) {} };
  let threw = false;
  try { o.m(null); } catch(e) { threw = (e instanceof TypeError); }
  assertEquals(true, threw);
}

// Async generators route through the same isGenerator() branch (the
// isAsync() branch is unreachable when isGenerator() is also true).
{
  async function* g({x}) {}
  let threw = false;
  try { g(null); } catch(e) { threw = (e instanceof TypeError); }
  assertEquals(true, threw);
}
