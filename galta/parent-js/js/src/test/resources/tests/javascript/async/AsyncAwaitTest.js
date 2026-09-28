// Basic async return behavior
async function f1() {
    return 42;
}

assertTrue(f1() instanceof Promise);
f1().then(v => assertEquals(42, v));

// Await with a resolved Promise
async function f2() {
    const v = await Promise.resolve(10);
    return v + 5;
}

f2().then(v => assertEquals(15, v));

// Await with a plain value
async function f3() {
    const v = await 7;
    return v * 3;
}

f3().then(v => assertEquals(21, v));

// Await with a rejected Promise
async function f4() {
    try {
        await Promise.reject("boom");
        fail(); // Should not reach
    } catch (e) {
        assertEquals("boom", e);
    }
}

f4();

// Nested async / await
async function f5() {
    async function inner() {
        const a = await 2;
        return a + 3;
    }
    const b = await inner();
    return b * 2;
}

f5().then(v => assertEquals(10, v));

// Sequential await
async function f6() {
    let a = await Promise.resolve(1);
    let b = await Promise.resolve(2);
    return a + b;
}

f6().then(v => assertEquals(3, v));

// Parallel awaits via Promise.all
async function f7() {
    const results = await Promise.all([Promise.resolve(1), Promise.resolve(2), Promise.resolve(3)]);
    return results.reduce((a, b) => a + b, 0);
}

f7().then(v => assertEquals(6, v));

// Awaiting an async function result
async function f8() {
    async function inner() {
        return 9;
    }
    const v = await inner();
    return v + 1;
}

f8().then(v => assertEquals(10, v));

// Await inside a normal function (should fail)
function f9() {
    try {
        eval("await 1;");
        fail();
    } catch (e) {
        assertTrue(e instanceof SyntaxError);
    }
}
f9();

// Async function throwing error
async function f10() {
    throw new Error("test");
}

f10().then(
    () => fail(),
    e => assertEquals("test", e.message)
);

// Awaiting chained async calls
async function f11() {
    const a = await f1(); // 42
    const b = await f3(); // 21
    return a + b;
}

f11().then(v => assertEquals(63, v));

// Top-level async IIFE
(async function testTopLevelAwait() {
    const x = await Promise.resolve(5);
    assertEquals(5, x);
})();

// Async function returning a promise chain
async function f12() {
    const x = await Promise.resolve(3);
    return Promise.resolve(x * 4);
}

f12().then(v => assertEquals(12, v));

// Async arrow functions
const add1 = async (a, b) => a + b;
add1(2, 3).then(v => assertEquals(5, v));

const add2 = async a => a + 1;
add2(4).then(v => assertEquals(5, v));

// An anonymous async arrow's inferred "name" is "" when not assigned/inferred.
assertEquals("", (async x => x).name);
assertEquals("", (async () => {}).name);
assertEquals("", (async function() {}).name);
assertEquals("", (function() {}).name);
assertEquals("", (x => x).name);

// async [no LineTerminator here] AsyncArrowBindingIdentifier: a line break between
// "async" and the parameter means "async" is just a plain identifier reference,
// not the start of an async arrow function.
assertThrows(ReferenceError, () => {
    eval("async\nidentifier => {}");
});
