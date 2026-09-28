// Async/Await Comprehensive Test Suite

// Test 1: Basic async function returns a Promise
async function testBasicAsyncFunction() {
    async function foo() {
        return 42;
    }
    
    const result = foo();
    assertTrue(result instanceof Promise);
    assertEquals(42, await result);
}

// Test 2: Async function with await
async function testBasicAwait() {
    async function getValue() {
        return 100;
    }
    
    async function useValue() {
        const val = await getValue();
        return val * 2;
    }
    
    assertEquals(200, await useValue());
}

// Test 3: Await on non-Promise value (should wrap in resolved Promise)
async function testAwaitNonPromise() {
    const result = await 42;
    assertEquals(42, result);
    
    const str = await "hello";
    assertEquals("hello", str);
    
    const obj = await {x: 1};
    assertEquals(1, obj.x);
}

// Test 4: Await on already resolved Promise
async function testAwaitResolvedPromise() {
    const p = Promise.resolve(123);
    const result = await p;
    assertEquals(123, result);
}

// Test 5: Await on already rejected Promise
async function testAwaitRejectedPromise() {
    const p = Promise.reject(new Error("test error"));
    
    try {
        await p;
        fail();
    } catch (e) {
        assertEquals("test error", e.message);
    }
}

// Test 6: Multiple awaits in sequence
async function testMultipleAwaits() {
    async function step1() { return 1; }
    async function step2() { return 2; }
    async function step3() { return 3; }
    
    const a = await step1();
    const b = await step2();
    const c = await step3();
    
    assertEquals(6, a + b + c);
}

// Test 7: Nested async functions
async function testNestedAsync() {
    async function outer() {
        async function inner() {
            return 50;
        }
        return await inner() + 10;
    }
    
    assertEquals(60, await outer());
}

// Test 8: Async function with try-catch
async function testAsyncTryCatch() {
    async function throwError() {
        throw new Error("async error");
    }
    
    async function handleError() {
        try {
            await throwError();
            return "no error";
        } catch (e) {
            return "caught: " + e.message;
        }
    }
    
    assertEquals("caught: async error", await handleError());
}

// Test 9: Async function throwing before await
async function testThrowBeforeAwait() {
    async function foo() {
        throw new Error("immediate throw");
        await Promise.resolve();
    }
    
    try {
        await foo();
        fail();
    } catch (e) {
        assertEquals("immediate throw", e.message);
    }
}

// Test 10: Async function throwing after await
async function testThrowAfterAwait() {
    async function foo() {
        await Promise.resolve();
        throw new Error("throw after await");
    }
    
    try {
        await foo();
        fail();
    } catch (e) {
        assertEquals("throw after await", e.message);
    }
}

// Test 11: Return value from async function without await
async function testAsyncReturnWithoutAwait() {
    async function foo() {
        return 77;
    }
    
    const result = await foo();
    assertEquals(77, result);
}

// Test 12: Empty async function
async function testEmptyAsyncFunction() {
    async function empty() {}
    
    const result = await empty();
    assertUndefined(result);
}

// Test 13: Async function with explicit undefined return
async function testAsyncExplicitUndefined() {
    async function foo() {
        return undefined;
    }
    
    const result = await foo();
    assertUndefined(result);
}

// Test 14: Await in conditional
async function testAwaitInConditional() {
    async function getTrue() { return true; }
    async function getFalse() { return false; }
    
    if (await getTrue()) {
        assertTrue(true);
    } else {
        fail();
    }
    
    if (await getFalse()) {
        fail();
    } else {
        assertTrue(true);
    }
}

// Test 15: Await in loop
async function testAwaitInLoop() {
    async function getValue(n) {
        return n * n;
    }
    
    let sum = 0;
    for (let i = 1; i <= 3; i++) {
        sum += await getValue(i);
    }
    
    assertEquals(14, sum); // 1 + 4 + 9
}

// Test 16: Promise.all with async/await
async function testPromiseAllWithAwait() {
    async function a() { return 1; }
    async function b() { return 2; }
    async function c() { return 3; }
    
    const results = await Promise.all([a(), b(), c()]);
    assertEquals(1, results[0]);
    assertEquals(2, results[1]);
    assertEquals(3, results[2]);
}

// Test 17: Promise.race with async/await
async function testPromiseRaceWithAwait() {
    const results = await Promise.race([
        Promise.resolve(1),
        Promise.resolve(2)
    ]);
    
    assertEquals(1, results);
}

// Test 18: Async arrow function
async function testAsyncArrowFunction() {
    const foo = async () => 99;
    assertEquals(99, await foo());
    
    const bar = async (x) => x * 2;
    assertEquals(10, await bar(5));
}

// Test 19: Async method in object literal
async function testAsyncMethod() {
    const obj = {
        async getValue() {
            return 42;
        }
    };
    
    assertEquals(42, await obj.getValue());
}

// Test 20: Async method in class
async function testAsyncClassMethod() {
    class MyClass {
        async compute() {
            return 100;
        }
    }
    
    const instance = new MyClass();
    assertEquals(100, await instance.compute());
}

// Test 21: Static async method
async function testStaticAsyncMethod() {
    class MyClass {
        static async getData() {
            return 200;
        }
    }
    
    assertEquals(200, await MyClass.getData());
}

// Test 22: Await with then-able object (thenable)
async function testAwaitThenable() {
    const thenable = {
        then(resolve, reject) {
            resolve(555);
        }
    };
    
    const result = await thenable;
    assertEquals(555, result);
}

// Test 23: Await with rejecting thenable
async function testAwaitRejectingThenable() {
    const thenable = {
        then(resolve, reject) {
            reject(new Error("thenable rejected"));
        }
    };
    
    try {
        await thenable;
        fail();
    } catch (e) {
        assertEquals("thenable rejected", e.message);
    }
}

// Test 24: Multiple async functions calling each other
async function testMultipleAsyncCalls() {
    async function level1() {
        return await level2() + 1;
    }
    
    async function level2() {
        return await level3() + 1;
    }
    
    async function level3() {
        return 1;
    }
    
    assertEquals(3, await level1());
}

// Test 25: Deep async call stack
async function testDeepAsyncStack() {
    async function recurse(n) {
        if (n === 0) return 0;
        return (await recurse(n - 1)) + 1;
    }
    
    assertEquals(10, await recurse(10));
}

// Test 26: Async function with finally block
async function testAsyncFinally() {
    let finallyCalled = false;
    
    async function foo() {
        try {
            return 42;
        } finally {
            finallyCalled = true;
        }
    }
    
    assertEquals(42, await foo());
    assertTrue(finallyCalled);
}

// Test 27: Async function with finally block and throw
async function testAsyncFinallyWithThrow() {
    let finallyCalled = false;
    
    async function foo() {
        try {
            throw new Error("test");
        } finally {
            finallyCalled = true;
        }
    }
    
    try {
        await foo();
        fail();
    } catch (e) {
        assertEquals("test", e.message);
        assertTrue(finallyCalled);
    }
}

// Test 28: Return Promise from async function
async function testReturnPromiseFromAsync() {
    async function foo() {
        return Promise.resolve(888);
    }
    
    assertEquals(888, await foo());
}

// Test 29: Return rejected Promise from async function
async function testReturnRejectedPromiseFromAsync() {
    async function foo() {
        return Promise.reject(new Error("rejected promise"));
    }
    
    try {
        await foo();
        fail();
    } catch (e) {
        assertEquals("rejected promise", e.message);
    }
}

// Test 30: Await in expression
async function testAwaitInExpression() {
    async function getValue() {
        return 5;
    }
    
    const result = (await getValue()) * 10 + 3;
    assertEquals(53, result);
}

// Test 31: Multiple awaits in single expression
async function testMultipleAwaitsInExpression() {
    async function getA() { return 2; }
    async function getB() { return 3; }
    
    const result = (await getA()) * (await getB()) + (await getA());
    assertEquals(8, result); // 2 * 3 + 2
}

// Test 32: Await in array literal
async function testAwaitInArrayLiteral() {
    async function getVal() { return 10; }
    
    const arr = [1, await getVal(), 20];
    assertEquals(1, arr[0]);
    assertEquals(10, arr[1]);
    assertEquals(20, arr[2]);
}

// Test 33: Await in object literal
async function testAwaitInObjectLiteral() {
    async function getVal() { return 100; }
    
    const obj = {
        x: 1,
        y: await getVal(),
        z: 3
    };
    
    assertEquals(1, obj.x);
    assertEquals(100, obj.y);
    assertEquals(3, obj.z);
}

// Test 34: Await with ternary operator
async function testAwaitWithTernary() {
    async function getTrue() { return true; }
    
    const result = (await getTrue()) ? "yes" : "no";
    assertEquals("yes", result);
}

// Test 35: Async function returning null
async function testAsyncReturnNull() {
    async function foo() {
        return null;
    }
    
    const result = await foo();
    assertNull(result);
}

// Test 36: Async function returning false
async function testAsyncReturnFalse() {
    async function foo() {
        return false;
    }
    
    const result = await foo();
    assertFalse(result);
}

// Test 37: Async function returning 0
async function testAsyncReturnZero() {
    async function foo() {
        return 0;
    }
    
    const result = await foo();
    assertEquals(0, result);
}

// Test 38: Chained then after async function
async function testChainedThen() {
    async function foo() {
        return 10;
    }
    
    let result;
    await foo().then(val => {
        result = val * 2;
    });
    
    assertEquals(20, result);
}

// Test 39: Catch after async function
async function testCatchAfterAsync() {
    async function foo() {
        throw new Error("test error");
    }
    
    let caught = false;
    await foo().catch(e => {
        caught = true;
    });
    
    assertTrue(caught);
}

// Test 40: Async IIFE (Immediately Invoked Function Expression)
async function testAsyncIIFE() {
    const result = await (async () => {
        return 777;
    })();
    
    assertEquals(777, result);
}

// Test 41: Await in switch statement
async function testAwaitInSwitch() {
    async function getVal() { return 2; }
    
    let result;
    switch (await getVal()) {
        case 1:
            result = "one";
            break;
        case 2:
            result = "two";
            break;
        default:
            result = "other";
    }
    
    assertEquals("two", result);
}

// Test 42: Await in while loop
async function testAwaitInWhile() {
    async function getValue(n) { return n; }
    
    let count = 0;
    let i = 0;
    while (await getValue(i) < 3) {
        count++;
        i++;
    }
    
    assertEquals(3, count);
}

// Test 43: Await in do-while loop
async function testAwaitInDoWhile() {
    async function getValue(n) { return n; }
    
    let count = 0;
    let i = 0;
    do {
        count++;
        i++;
    } while (await getValue(i) < 3);
    
    assertEquals(3, count);
}

// Test 44: Await with logical AND
async function testAwaitWithLogicalAnd() {
    async function getTrue() { return true; }
    async function getFalse() { return false; }
    
    assertTrue(await getTrue() && await getTrue());
    assertFalse(await getTrue() && await getFalse());
    assertFalse(await getFalse() && await getTrue());
}

// Test 45: Await with logical OR
async function testAwaitWithLogicalOr() {
    async function getTrue() { return true; }
    async function getFalse() { return false; }
    
    assertTrue(await getTrue() || await getFalse());
    assertTrue(await getFalse() || await getTrue());
    assertFalse(await getFalse() || await getFalse());
}

// Test 46: Nested try-catch-finally with await
async function testNestedTryCatchFinally() {
    let finallyCount = 0;
    
    async function foo() {
        try {
            try {
                throw new Error("inner");
            } finally {
                finallyCount++;
            }
        } catch (e) {
            return e.message;
        } finally {
            finallyCount++;
        }
    }
    
    assertEquals("inner", await foo());
    assertEquals(2, finallyCount);
}

// Test 47: Await in function argument
async function testAwaitInFunctionArgument() {
    async function getValue() { return 5; }
    
    function multiply(x, y) {
        return x * y;
    }
    
    const result = multiply(await getValue(), 10);
    assertEquals(50, result);
}

// Test 48: Multiple awaits in function arguments
async function testMultipleAwaitsInArguments() {
    async function getX() { return 3; }
    async function getY() { return 4; }
    
    function add(a, b) {
        return a + b;
    }
    
    const result = add(await getX(), await getY());
    assertEquals(7, result);
}

// Test 49: Async generator-like pattern (manual iteration)
async function testAsyncIterationPattern() {
    async function* gen() {
        yield 1;
        yield 2;
        yield 3;
    }
    
    const iterator = gen();
    
    assertEquals(1, (await iterator.next()).value);
    assertEquals(2, (await iterator.next()).value);
    assertEquals(3, (await iterator.next()).value);
    assertTrue((await iterator.next()).done);
}

// Test 50: Async function with complex control flow
async function testComplexControlFlow() {
    async function getValue(n) { return n; }
    
    let result = 0;
    for (let i = 0; i < 5; i++) {
        if (await getValue(i) % 2 === 0) {
            result += await getValue(i);
        } else {
            result -= await getValue(i);
        }
    }
    
    assertEquals(2, result); // 0 - 1 + 2 - 3 + 4 = 2
}

// Test 51: Re-awaiting same promise
async function testReAwaitSamePromise() {
    const promise = Promise.resolve(123);
    
    const result1 = await promise;
    const result2 = await promise;
    
    assertEquals(123, result1);
    assertEquals(123, result2);
}

// Test 52: Async function with rest parameters
async function testAsyncWithRestParams() {
    async function sum(...nums) {
        return nums.reduce((a, b) => a + b, 0);
    }
    
    assertEquals(15, await sum(1, 2, 3, 4, 5));
}

// Test 53: Async function with destructuring
async function testAsyncWithDestructuring() {
    async function getObject() {
        return { x: 10, y: 20 };
    }
    
    const { x, y } = await getObject();
    assertEquals(10, x);
    assertEquals(20, y);
}

// Test 54: Async function with array destructuring
async function testAsyncWithArrayDestructuring() {
    async function getArray() {
        return [100, 200, 300];
    }
    
    const [a, b, c] = await getArray();
    assertEquals(100, a);
    assertEquals(200, b);
    assertEquals(300, c);
}

// Test 55: Await with comma operator
async function testAwaitWithCommaOperator() {
    async function getVal() { return 5; }
    
    const result = (1, 2, await getVal());
    assertEquals(5, result);
}

// Test 56: Async function returning another async function
async function testAsyncReturningAsync() {
    async function inner() {
        return 42;
    }
    
    async function outer() {
        return inner;
    }
    
    const fn = await outer();
    assertEquals(42, await fn());
}

// Test 57: Throw non-Error object from async function
async function testThrowNonError() {
    async function foo() {
        throw "string error";
    }
    
    try {
        await foo();
        fail();
    } catch (e) {
        assertEquals("string error", e);
    }
}

// Test 58: Throw number from async function
async function testThrowNumber() {
    async function foo() {
        throw 404;
    }
    
    try {
        await foo();
        fail();
    } catch (e) {
        assertEquals(404, e);
    }
}

// Test 59: Await in template literal
async function testAwaitInTemplateLiteral() {
    async function getName() { return "World"; }
    
    const greeting = `Hello, ${await getName()}!`;
    assertEquals("Hello, World!", greeting);
}

// Test 60: Await with spread operator
async function testAwaitWithSpread() {
    async function getArray() {
        return [1, 2, 3];
    }
    
    const arr = [0, ...await getArray(), 4];
    assertEquals(0, arr[0]);
    assertEquals(1, arr[1]);
    assertEquals(2, arr[2]);
    assertEquals(3, arr[3]);
    assertEquals(4, arr[4]);
}

// Test 61: Promise.resolve with async value
async function testPromiseResolveWithAsync() {
    async function getValue() {
        return 999;
    }
    
    const result = await Promise.resolve(getValue());
    assertEquals(999, await result);
}

// Test 62: Nested async expressions
async function testNestedAsyncExpressions() {
    async function a() { return 1; }
    async function b() { return 2; }
    async function c() { return 3; }
    
    const result = await a() + await b() * await c();
    assertEquals(7, result); // 1 + 2 * 3 = 1 + 6 = 7
}

// Test 63: Async function with getter
async function testAsyncWithGetter() {
    const obj = {
        _value: 42,
        async getValue() {
            return this._value;
        }
    };
    
    assertEquals(42, await obj.getValue());
}

// Test 64: Async function with this binding
async function testAsyncWithThisBinding() {
    const obj = {
        value: 100,
        async double() {
            return this.value * 2;
        }
    };
    
    assertEquals(200, await obj.double());
}

// Test 65: Error in Promise constructor with async
async function testErrorInPromiseConstructor() {
    const promise = new Promise((resolve, reject) => {
        throw new Error("constructor error");
    });
    
    try {
        await promise;
        fail();
    } catch (e) {
        assertEquals("constructor error", e.message);
    }
}

// Test 66: Async function with no explicit return
async function testAsyncNoExplicitReturn() {
    async function foo() {
        let x = 1 + 1;
    }
    
    const result = await foo();
    assertUndefined(result);
}

// Test 67: Double await
async function testDoubleAwait() {
    async function getValue() {
        return Promise.resolve(77);
    }
    
    const result = await await getValue();
    assertEquals(77, result);
}

// Test 68: Triple await
async function testTripleAwait() {
    async function getValue() {
        return Promise.resolve(Promise.resolve(88));
    }
    
    const result = await await await getValue();
    assertEquals(88, result);
}

// Test 69: Await with void operator
async function testAwaitWithVoid() {
    async function foo() {
        return 42;
    }
    
    const result = void await foo();
    assertUndefined(result);
}

// Test 70: Await with typeof operator
async function testAwaitWithTypeof() {
    async function getValue() {
        return 42;
    }
    
    const type = typeof (await getValue());
    assertEquals("number", type);
}

// Test 71: "await" is a plain identifier (not a keyword) inside a plain function
// nested inside an async function, so assigning to it must affect the outer binding.
// ("await" is only reserved directly within an async function's own body, so the
// declaration and the assertion below - run once foo() truly completes - both live
// outside of one; a bare arrow passed to .then() is not itself inside foo's body.)
function testAwaitAsIdentifierInNestedFunction() {
    var await;
    async function foo() {
        function bar() {
            await = 1;
        }
        bar();
    }
    return foo().then(() => {
        assertEquals(1, await);
    });
}

// Run all tests
async function runAllTests() {
    const tests = [
        testBasicAsyncFunction,
        testBasicAwait,
        testAwaitNonPromise,
        testAwaitResolvedPromise,
        testAwaitRejectedPromise,
        testMultipleAwaits,
        testNestedAsync,
        testAsyncTryCatch,
        testThrowBeforeAwait,
        testThrowAfterAwait,
        testAsyncReturnWithoutAwait,
        testEmptyAsyncFunction,
        testAsyncExplicitUndefined,
        testAwaitInConditional,
        testAwaitInLoop,
        testPromiseAllWithAwait,
        testPromiseRaceWithAwait,
        testAsyncArrowFunction,
        testAsyncMethod,
        testAsyncClassMethod,
        testStaticAsyncMethod,
        testAwaitThenable,
        testAwaitRejectingThenable,
        testMultipleAsyncCalls,
        testDeepAsyncStack,
        testAsyncFinally,
        testAsyncFinallyWithThrow,
        testReturnPromiseFromAsync,
        testReturnRejectedPromiseFromAsync,
        testAwaitInExpression,
        testMultipleAwaitsInExpression,
        testAwaitInArrayLiteral,
        testAwaitInObjectLiteral,
        testAwaitWithTernary,
        testAsyncReturnNull,
        testAsyncReturnFalse,
        testAsyncReturnZero,
        testChainedThen,
        testCatchAfterAsync,
        testAsyncIIFE,
        testAwaitInSwitch,
        testAwaitInWhile,
        testAwaitInDoWhile,
        testAwaitWithLogicalAnd,
        testAwaitWithLogicalOr,
        testNestedTryCatchFinally,
        testAwaitInFunctionArgument,
        testMultipleAwaitsInArguments,
        testAsyncIterationPattern,
        testComplexControlFlow,
        testReAwaitSamePromise,
        testAsyncWithRestParams,
        testAsyncWithDestructuring,
        testAsyncWithArrayDestructuring,
        testAwaitWithCommaOperator,
        testAsyncReturningAsync,
        testThrowNonError,
        testThrowNumber,
        testAwaitInTemplateLiteral,
        testAwaitWithSpread,
        testPromiseResolveWithAsync,
        testNestedAsyncExpressions,
        testAsyncWithGetter,
        testAsyncWithThisBinding,
        testErrorInPromiseConstructor,
        testAsyncNoExplicitReturn,
        testDoubleAwait,
        testTripleAwait,
        testAwaitWithVoid,
        testAwaitWithTypeof,
        testAwaitAsIdentifierInNestedFunction
    ];
    
    for (const test of tests) {
        try {
            await test();
        } catch (e) {
            throw new Error(`Test ${test.name} failed: ${e.message}`);
        }
    }
}

// Execute all tests
await runAllTests();