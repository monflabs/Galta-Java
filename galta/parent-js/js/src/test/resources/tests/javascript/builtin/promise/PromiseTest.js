// promise.tests.js

// ---------- Small test harness helpers ----------
const tests = [];
function testAsync(name, fn) {
  tests.push(
    Promise.resolve()
      .then(fn)
      .catch((e) => {
        // Surface which test failed, then fail the whole run.
        try { console.error("Test failed:", name, e && e.stack || e); } catch {}
        throw e;
      })
  );
}
function mustNotCall(label) {
  return function () {
    try { console.error("mustNotCall invoked:", label); } catch {}
    //fail(); // This is not catchable
	throw new Error();
  };
}
function delay(ms) {
  // Uses Promise; avoids setTimeout reliance if timer APIs aren't available in the runtime.
  // If timers are not present, a microtask-only delay:
  if (typeof setTimeout !== "function") return Promise.resolve();
  return new Promise((r) => setTimeout(r, ms));
}

// ---------- Synchronous shape & property tests ----------
(function syncShapeTests() {
  // Basic existence & types
  assertEqualsStrict("function", typeof Promise);
  assertEqualsStrict("object", typeof Promise.prototype);
  assertTrue("then" in Promise.prototype);
  assertTrue("catch" in Promise.prototype);
  assertTrue("finally" in Promise.prototype);

  // Constructor cannot be called without 'new'
  assertThrows(TypeError, () => { Promise(function () {}); });

  // Executor must be callable
  assertThrows(TypeError, () => { new Promise({}); });
  assertThrows(TypeError, () => { new Promise(undefined); });
  assertThrows(TypeError, () => { new Promise(null); });

  // Length (arity) checks (as per ECMAScript)
  assertEqualsStrict(1, Promise.length);
  assertEqualsStrict(2, Promise.prototype.then.length);
  assertEqualsStrict(1, Promise.prototype.catch.length);
  assertEqualsStrict(1, Promise.prototype.finally.length);
  assertEqualsStrict(1, Promise.resolve.length);
  assertEqualsStrict(1, Promise.reject.length);
  assertEqualsStrict(1, Promise.race.length);
  assertEqualsStrict(1, Promise.all.length);
  assertEqualsStrict(1, Promise.allSettled.length);
  assertEqualsStrict(1, Promise.any.length);

  // Constructor & prototype linkage
  assertEqualsStrict(Promise, Promise.prototype.constructor);

  // toStringTag (if Symbols supported)
  if (typeof Symbol === "function" && Symbol.toStringTag) {
    assertEqualsStrict("Promise", Promise.prototype[Symbol.toStringTag]);
  }
})();

// ---------- Constructor behavior & resolution/rejection ----------
testAsync("constructor invokes executor synchronously", () => {
  let order = [];
  new Promise((resolve) => {
    order.push("executor:start");
    resolve(1);
    order.push("executor:end");
  }).then(() => {
    order.push("then");
    assertEqualsStrict("executor:start,executor:end,then", order.join(","));
  });
});

testAsync("executor throwing rejects the promise", () => {
  const err = new Error("boom");
  return new Promise(() => { throw err; })
    .then(mustNotCall("executor-throw-then"))
    .catch((e) => {
      assertEqualsStrict(err, e);
    });
});

testAsync("resolve then reject ignored (single resolution)", () => {
  let count = 0;
  return new Promise((resolve, reject) => {
    resolve(42);
    reject(new Error("ignored"));
  })
    .then((v) => { count++; assertEqualsStrict(42, v); })
    .then(() => { assertEqualsStrict(1, count); });
});

testAsync("reject then resolve ignored (single resolution)", () => {
  let seen = false;
  return new Promise((resolve, reject) => {
    reject("nope");
    resolve("ignored");
  })
    .then(mustNotCall("resolved-after-reject"))
    .catch((r) => { seen = true; assertEqualsStrict("nope", r); })
    .then(() => assertTrue(seen));
});

testAsync("resolving with self rejects with TypeError", () => {
  const p = new Promise((resolve) => {
    // Defer to ensure `p` is bound
    Promise.resolve().then(() => resolve(p));
  });
  return p.then(mustNotCall("self-resolution"))
          .catch((e) => assertTrue(e instanceof TypeError));
});

// ---------- Then/Catch/Finally semantics ----------
testAsync(".then returns a promise and chains values", () => {
  return Promise.resolve(10)
    .then((v) => { assertEqualsStrict(10, v); return v + 1; })
    .then((v) => { assertEqualsStrict(11, v); });
});

testAsync(".then with non-callables acts as passthrough", () => {
  return Promise.resolve(7)
    .then(null)
    .then(undefined)
    .then((v) => { assertEqualsStrict(7, v); });
});

testAsync(".catch is sugar for then(undefined, onRejected)", () => {
  return Promise.reject("err")
    .catch((r) => { assertEqualsStrict("err", r); return 99; })
    .then((v) => { assertEqualsStrict(99, v); });
});

testAsync(".finally passes through fulfillment value when handler returns non-throwing", () => {
  return Promise.resolve("ok")
    .finally(() => { /* no-op */ })
    .then((v) => { assertEqualsStrict("ok", v); });
});

testAsync(".finally waits for returned promise and still passes through", () => {
  let waited = false;
  return Promise.resolve(1)
    .finally(() => Promise.resolve().then(() => { waited = true; }))
    .then((v) => { assertTrue(waited); assertEqualsStrict(1, v); });
});

testAsync(".finally turning into throw switches to rejection", () => {
  return Promise.resolve("x")
    .finally(() => { throw new Error("boom"); })
    .then(mustNotCall("finally-throw-then"))
    .catch((e) => { assertEqualsStrict("boom", e.message); });
});

testAsync(".finally returning rejected promise switches to rejection", () => {
  return Promise.resolve("x")
    .finally(() => Promise.reject("nope"))
    .then(mustNotCall("finally-rejected-then"))
    .catch((r) => { assertEqualsStrict("nope", r); });
});

testAsync("then on already-fulfilled promise still runs asynchronously", () => {
  let flag = 0;
  const p = Promise.resolve(5);
  p.then(() => { assertEqualsStrict(1, flag); });
  flag = 1;
  return p.then(() => {}); // ensure microtask drained
});

testAsync("then-handler throwing rejects returned promise", () => {
  const err = new Error("throw in then");
  return Promise.resolve(1)
    .then(() => { throw err; })
    .then(mustNotCall("then-throw-then"))
    .catch((e) => { assertEqualsStrict(err, e); });
});

// ---------- Brand checks: methods require a Promise receiver ----------
testAsync("Promise.prototype.then brand check", () => {
  assertThrows(TypeError, () => Promise.prototype.then.call({}, () => {}));
});
testAsync("Promise.prototype.catch brand check", () => {
  assertThrows(TypeError, () => Promise.prototype.catch.call({}, () => {}));
});
testAsync("Promise.prototype.finally brand check", () => {
  assertThrows(TypeError, () => Promise.prototype.finally.call({}, () => {}));
});

// ---------- Static: Promise.resolve ----------
testAsync("Promise.resolve returns same promise if input is a promise", () => {
  const p = Promise.resolve(1);
  assertEqualsStrict(p, Promise.resolve(p));
  return p.then((v) => assertEqualsStrict(1, v));
});

testAsync("Promise.resolve assimilates thenable", () => {
  const thenable = { then(res) { res("T"); } };
  return Promise.resolve(thenable)
    .then((v) => { assertEqualsStrict("T", v); });
});

testAsync("Promise.resolve with thenable whose 'then' getter throws -> reject", () => {
  const t = Object.create(null);
  Object.defineProperty(t, "then", { get() { throw new Error("bad getter"); }});
  return Promise.resolve(t)
    .then(mustNotCall("thenable-getter-throw"))
    .catch((e) => { assertEqualsStrict("bad getter", e.message); });
});

testAsync("Promise.resolve with then not callable treats as value", () => {
  const t = { then: 123, x: 9 };
  return Promise.resolve(t)
    .then((v) => { assertEqualsStrict(t, v); });
});

// ---------- Static: Promise.reject ----------
testAsync("Promise.reject creates a rejected promise with reason", () => {
  const reason = { code: 404 };
  return Promise.reject(reason)
    .then(mustNotCall("reject-then"))
    .catch((r) => { assertEqualsStrict(reason, r); });
});

// ---------- Static: Promise.all ----------
testAsync("Promise.all with empty iterable resolves to []", () => {
  return Promise.all([])
    .then((arr) => { assertEqualsStrict(0, arr.length); });
});

testAsync("Promise.all resolves in input order", () => {
  const a = Promise.resolve("a");
  const b = Promise.resolve("b");
  const c = Promise.resolve("c");
  return Promise.all([a, b, c])
    .then((arr) => {
      assertEqualsStrict("a", arr[0]);
      assertEqualsStrict("b", arr[1]);
      assertEqualsStrict("c", arr[2]);
    });
});

testAsync("Promise.all rejects with first rejection", () => {
  const e = new Error("first");
  const p1 = delay(0).then(() => "ok");
  const p2 = Promise.reject(e);
  const p3 = delay(0).then(() => "late");
  return Promise.all([p1, p2, p3])
    .then(mustNotCall("all-then-on-reject"))
    .catch((err) => { assertEqualsStrict(e, err); });
});

testAsync("Promise.all accepts thenables", () => {
  const t1 = { then(res) { res(1); } };
  const t2 = { then(res) { res(2); } };
  return Promise.all([t1, t2]).then((arr) => {
    assertEqualsStrict(1, arr[0]);
    assertEqualsStrict(2, arr[1]);
  });
});

testAsync("Promise.all with non-iterable rejects with TypeError", () => {
  return Promise.all(null)
    .then(mustNotCall("all-null-then"))
    .catch((e) => { assertTrue(e instanceof TypeError); })
    .then(() => Promise.all(123))
    .then(mustNotCall("all-123-then"))
    .catch((e) => { assertTrue(e instanceof TypeError); });
});

// ---------- Static: Promise.allSettled ----------
testAsync("Promise.allSettled empty iterable -> []", () => {
  return Promise.allSettled([]).then((arr) => {
    assertEqualsStrict(0, arr.length);
  });
});

testAsync("Promise.allSettled preserves order and shapes", () => {
  const p1 = Promise.resolve(1);
  const p2 = Promise.reject("e2");
  const p3 = { then(res) { res(3); } };
  return Promise.allSettled([p1, p2, p3]).then((arr) => {
    assertEqualsStrict(3, arr.length);
    assertEqualsStrict("fulfilled", arr[0].status);
    assertEqualsStrict(1, arr[0].value);
    assertEqualsStrict("rejected", arr[1].status);
    assertEqualsStrict("e2", arr[1].reason);
    assertEqualsStrict("fulfilled", arr[2].status);
    assertEqualsStrict(3, arr[2].value);
  });
});

testAsync("Promise.allSettled with non-iterable rejects with TypeError", () => {
  return Promise.allSettled(undefined)
    .then(mustNotCall("allSettled-undefined-then"))
    .catch((e) => { assertTrue(e instanceof TypeError); });
});

// ---------- Static: Promise.any ----------
testAsync("Promise.any resolves with first fulfillment", () => {
  const p1 = Promise.reject("no");
  const p2 = Promise.resolve("yes");
  const p3 = delay(0).then(() => "later");
  return Promise.any([p1, p2, p3]).then((v) => {
    assertEqualsStrict("yes", v);
  });
});

testAsync("Promise.any rejects with AggregateError when all reject", () => {
  const p1 = Promise.reject("a");
  const p2 = Promise.reject("b");
  return Promise.any([p1, p2])
    .then(mustNotCall("any-then-on-all-reject"))
    .catch((e) => {
      // Spec requires AggregateError with .errors array
      assertTrue(typeof AggregateError === "function");
      assertTrue(e instanceof AggregateError);
      assertTrue(Array.isArray(e.errors));
      assertEqualsStrict(2, e.errors.length);
      assertEqualsStrict("a", e.errors[0]);
      assertEqualsStrict("b", e.errors[1]);
    });
});

testAsync("Promise.any on empty iterable rejects AggregateError", () => {
  return Promise.any([])
    .then(mustNotCall("any-empty-then"))
    .catch((e) => {
      assertTrue(e instanceof AggregateError);
      assertTrue(Array.isArray(e.errors));
      assertEqualsStrict(0, e.errors.length);
    });
});

testAsync("Promise.any with non-iterable rejects with TypeError", () => {
  return Promise.any(0)
    .then(mustNotCall("any-0-then"))
    .catch((e) => { assertTrue(e instanceof TypeError); });
});

// ---------- Static: Promise.race ----------
testAsync("Promise.race settles with first settlement (resolve)", () => {
  const fast = Promise.resolve("fast");
  const slow = delay(0).then(() => "slow");
  return Promise.race([slow, fast]).then((v) => {
    assertEqualsStrict("fast", v);
  });
});

testAsync("Promise.race settles with first settlement (reject)", () => {
  const err = new Error("boom");
  const rejectFast = Promise.reject(err);
  const resolveSlow = delay(0).then(() => 1);
  return Promise.race([resolveSlow, rejectFast])
    .then(mustNotCall("race-then-on-reject"))
    .catch((e) => { assertEqualsStrict(err, e); });
});

testAsync("Promise.race with non-iterable rejects with TypeError", () => {
  Promise.race("not-iterable"); // String is iterable (should not throw/reject)
  return Promise.race(234)
    .then(mustNotCall("race-234-then"))
    .catch((e) => { assertTrue(e instanceof TypeError); });
});

// ---------- Thenable assimilation pathological cases ----------
testAsync("thenable whose then calls both resolve and reject: first wins", () => {
  const t = {
    then(res, rej) { res("win"); rej("lose"); }
  };
  return Promise.resolve(t).then((v) => { assertEqualsStrict("win", v); });
});

testAsync("thenable that calls resolve multiple times: first wins", () => {
  const t = { then(res) { res(1); res(2); res(3); } };
  return Promise.resolve(t).then((v) => { assertEqualsStrict(1, v); });
});

testAsync("thenable that throws after resolve is ignored", () => {
  const t = { then(res) { res(1); throw new Error("late"); } };
  return Promise.resolve(t).then((v) => { assertEqualsStrict(1, v); });
});

// ---------- Subclassing & species ----------
testAsync("Subclassing Promise: then returns subclass instance by default species", () => {
  class MyPromise extends Promise {}
  const p = new MyPromise((res) => res(1));
  const q = p.then((v) => v + 1);
  // .then() uses SpeciesConstructor(this, Promise): Symbol.species DEFAULTS
  // to a getter returning `this` (the constructor itself), so an
  // unmodified subclass's own species IS the subclass - only an EXPLICIT
  // override (see the next test) changes which constructor gets used.
  assertTrue(q instanceof MyPromise);
  return q.then((v) => { assertEqualsStrict(2, v); });
});

testAsync("Subclassing with Symbol.species returning base Promise", () => {
  if (typeof Symbol === "function" && Symbol.species) {
    class Base extends Promise {
      static get [Symbol.species]() { return Promise; }
    }
    const p = new Base((res) => res(1));
    const q = p.then((v) => v);
    assertFalse(q instanceof Base);
    assertTrue(q instanceof Promise);
    return q;
  }
});

// ---------- Order & reentrancy corner ----------
testAsync("handler registered after resolve still runs (settled-then)", () => {
  const p = Promise.resolve(3);
  let seen = false;
  return Promise.resolve()
    .then(() => { return p; })
    .then((v) => { seen = true; assertEqualsStrict(3, v); })
    .then(() => { assertTrue(seen); });
});

testAsync("then callbacks run in order of registration (FIFO per microtask)", () => {
  const p = Promise.resolve("x");
  const order = [];
  p.then(() => order.push(1));
  p.then(() => order.push(2));
  p.then(() => order.push(3));
  return p.then(() => {
    assertEqualsStrict("1,2,3", order.join(","));
  });
});

// ---------- Combinators must call the overridable constructor.resolve ----------
// Regression: Promise.all/allSettled/any/race used to bypass a custom
// `resolve` override entirely, which - combined with a never-terminating
// iterator - spun forever and crashed the JVM with an OutOfMemoryError.
testAsync("Promise.all respects an overridden constructor.resolve", () => {
  let calls = 0;
  class C extends Promise {
    static resolve(v) { calls++; return Promise.resolve(v); }
  }
  return Promise.all.call(C, [1, 2, 3]).then(() => {
    assertEqualsStrict(3, calls);
  });
});

testAsync("Promise.all rejects synchronously (not hangs) when constructor.resolve throws", () => {
  class C extends Promise {
    static resolve() { throw new Error("nope"); }
  }
  return Promise.all.call(C, [1]).catch((e) => {
    assertEqualsStrict("nope", e.message);
  });
});

// Regression: NewPromiseCapability unconditionally cast the constructed
// object to the internal BuiltinPromise Java class, throwing a raw
// ClassCastException for a completely custom (non-Promise) constructor.
testAsync("Promise.all.call with a fully custom (non-Promise) constructor doesn't crash", () => {
  let called = false;
  const C = function (executor) {
    called = true;
    executor(function () {}, function () {});
  };
  C.resolve = function (v) { return v; };
  Promise.all.call(C, []);
  assertTrue(called);
});

// ---------- ctx-non-object: combinators must throw on a non-object `this` ----------
testAsync("Promise.all/allSettled/any/race throw TypeError on non-object this", () => {
  for (const name of ["all", "allSettled", "any", "race"]) {
    assertThrows(TypeError, () => Promise[name].call(undefined, []));
    assertThrows(TypeError, () => Promise[name].call(null, []));
    assertThrows(TypeError, () => Promise[name].call(42, []));
  }
});

// ---------- AlreadyCalled: a thenable invoking its callback twice is idempotent ----------
testAsync("Promise.all resolve element function only applies the first call", () => {
  const p1 = { then(res) { res("first"); res("second"); } };
  return Promise.all([p1]).then((arr) => {
    assertEqualsStrict("first", arr[0]);
  });
});

testAsync("Promise.allSettled resolve element function only applies the first call", () => {
  const p1 = { then(res) { res("first"); res("second"); } };
  return Promise.allSettled([p1]).then((arr) => {
    assertEqualsStrict("first", arr[0].value);
  });
});

// ---------- Promise.prototype.catch generically invokes .then ----------
testAsync("Promise.prototype.catch works on a non-Promise thenable", () => {
  let called = false;
  const obj = { then(onF, onR) { called = true; onR("reason"); } };
  return Promise.prototype.catch.call(obj, (r) => {
    assertTrue(called);
    assertEqualsStrict("reason", r);
  });
});

// ---------- Promise.try ----------
testAsync("Promise.try resolves with the callback's return value", () => {
  return Promise.try(() => 42).then((v) => assertEqualsStrict(42, v));
});

testAsync("Promise.try rejects if the callback throws synchronously", () => {
  const err = new Error("try-throw");
  return Promise.try(() => { throw err; })
    .then(mustNotCall("try-throw-then"))
    .catch((e) => assertEqualsStrict(err, e));
});

testAsync("Promise.try forwards extra arguments to the callback", () => {
  return Promise.try((a, b) => a + b, 1, 2).then((v) => assertEqualsStrict(3, v));
});

// ---------- Promise.withResolvers ----------
testAsync("Promise.withResolvers returns {promise, resolve, reject}", () => {
  const { promise, resolve, reject } = Promise.withResolvers();
  assertTrue(promise instanceof Promise);
  assertEqualsStrict("function", typeof resolve);
  assertEqualsStrict("function", typeof reject);
  resolve("done");
  return promise.then((v) => assertEqualsStrict("done", v));
});

// ---------- Run all ----------
Promise.all(tests)
  .then(() => {
    try { console.log("All Promise tests passed:", tests.length); } catch {}
  })
  .catch((e) => {
    // Ensure the harness marks failure even if console is ignored.
    fail();
  });