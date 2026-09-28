// --- Object ---
assertEquals("[object Function]", Object.prototype.toString.call(Object));
assertEquals("[object Object]", Object.prototype.toString.call(Object.prototype));
assertEquals("[object Object]", Object.prototype.toString.call(new Object()));
assertEquals("[object Object]", Object.prototype.toString.call(({}).__proto__));
assertEquals(undefined, Object.prototype[Symbol.toStringTag]);
assertEquals(undefined, ({}).__proto__[Symbol.toStringTag]);

// --- Function ---
assertEquals("[object Function]", Object.prototype.toString.call(Function));
assertEquals("[object Function]", Object.prototype.toString.call(Function.prototype));
assertEquals("[object Function]", Object.prototype.toString.call(function() {}));
assertEquals("[object Function]", Object.prototype.toString.call((function() {}).__proto__));
assertEquals(undefined, Function.prototype[Symbol.toStringTag]);
assertEquals(undefined, (function() {}).__proto__[Symbol.toStringTag]);

// --- Array ---
assertEquals("[object Function]", Object.prototype.toString.call(Array));
assertEquals("[object Array]", Object.prototype.toString.call(Array.prototype));
assertEquals("[object Array]", Object.prototype.toString.call([]));
assertEquals("[object Array]", Object.prototype.toString.call([].__proto__));
assertEquals(undefined, Array.prototype[Symbol.toStringTag]);
assertEquals(undefined, [].__proto__[Symbol.toStringTag]);

// --- String ---
assertEquals("[object Function]", Object.prototype.toString.call(String));
assertEquals("[object String]", Object.prototype.toString.call(String.prototype));
assertEquals("[object String]", Object.prototype.toString.call(new String("x")));
assertEquals("[object String]", Object.prototype.toString.call((new String("x")).__proto__));
assertEquals(undefined, String.prototype[Symbol.toStringTag]);
assertEquals(undefined, (new String("x")).__proto__[Symbol.toStringTag]);

// --- Number ---
assertEquals("[object Function]", Object.prototype.toString.call(Number));
assertEquals("[object Number]", Object.prototype.toString.call(Number.prototype));
assertEquals("[object Number]", Object.prototype.toString.call(new Number(1)));
assertEquals("[object Number]", Object.prototype.toString.call((new Number(1)).__proto__));
assertEquals(undefined, Number.prototype[Symbol.toStringTag]);
assertEquals(undefined, (new Number(1)).__proto__[Symbol.toStringTag]);

// --- Boolean ---
assertEquals("[object Function]", Object.prototype.toString.call(Boolean));
assertEquals("[object Boolean]", Object.prototype.toString.call(Boolean.prototype));
assertEquals("[object Boolean]", Object.prototype.toString.call(new Boolean(true)));
assertEquals("[object Boolean]", Object.prototype.toString.call((new Boolean(true)).__proto__));
assertEquals(undefined, Boolean.prototype[Symbol.toStringTag]);
assertEquals(undefined, (new Boolean(true)).__proto__[Symbol.toStringTag]);

// --- BigInt ---
assertEquals("[object Function]", Object.prototype.toString.call(BigInt));
assertEquals("[object BigInt]", Object.prototype.toString.call(BigInt.prototype));
assertEquals("[object BigInt]", Object.prototype.toString.call(BigInt(1)));
assertEquals("[object BigInt]", Object.prototype.toString.call(BigInt(1).__proto__));
assertEquals("BigInt", BigInt.prototype[Symbol.toStringTag]);
assertEquals("BigInt", BigInt(1).__proto__[Symbol.toStringTag]);

// --- Decimal ---
if (typeof Decimal === "function") {
    assertEquals("[object Function]", Object.prototype.toString.call(Decimal));
    assertEquals("[object Decimal]", Object.prototype.toString.call(Decimal.prototype));
    assertEquals("[object Decimal]", Object.prototype.toString.call(Decimal(1)));
    assertEquals("[object Decimal]", Object.prototype.toString.call(Decimal(1).__proto__));
    assertEquals("Decimal", Decimal.prototype[Symbol.toStringTag]);
    assertEquals("Decimal", Decimal(1).__proto__[Symbol.toStringTag]);
}

// --- Symbol (optional) ---
if (typeof Symbol === "function") {
    assertEquals("[object Function]", Object.prototype.toString.call(Symbol));
    assertEquals("[object Symbol]", Object.prototype.toString.call(Symbol.prototype));
    assertEquals("[object Symbol]", Object.prototype.toString.call(Object(Symbol("x"))));
    assertEquals("[object Symbol]", Object.prototype.toString.call(Object(Symbol("x")).__proto__));
    assertEquals("Symbol", Symbol.prototype[Symbol.toStringTag]);
    assertEquals("Symbol", Object(Symbol("x")).__proto__[Symbol.toStringTag]);
}

// --- Error and subclasses ---
assertEquals("[object Function]", Object.prototype.toString.call(Error));
assertEquals("[object Object]", Object.prototype.toString.call(Error.prototype));
assertEquals("[object Error]", Object.prototype.toString.call(new Error()));
assertEquals("[object Object]", Object.prototype.toString.call((new Error()).__proto__));
// V8: Error.prototype[@@toStringTag] is undefined; tag comes from [[ErrorData]].
assertEquals(undefined, Error.prototype[Symbol.toStringTag]);
assertEquals(undefined, (new Error()).__proto__[Symbol.toStringTag]);

if (typeof EvalError === "function") {
    assertEquals("[object Function]", Object.prototype.toString.call(EvalError));
    assertEquals("[object Object]", Object.prototype.toString.call(EvalError.prototype));
    assertEquals("[object Error]", Object.prototype.toString.call(new EvalError()));
    assertEquals("[object Object]", Object.prototype.toString.call((new EvalError()).__proto__));
    assertEquals(undefined, EvalError.prototype[Symbol.toStringTag]);
    assertEquals(undefined, (new EvalError()).__proto__[Symbol.toStringTag]);
}

if (typeof RangeError === "function") {
    assertEquals("[object Function]", Object.prototype.toString.call(RangeError));
    assertEquals("[object Object]", Object.prototype.toString.call(RangeError.prototype));
    assertEquals("[object Error]", Object.prototype.toString.call(new RangeError()));
    assertEquals("[object Object]", Object.prototype.toString.call((new RangeError()).__proto__));
    assertEquals(undefined, RangeError.prototype[Symbol.toStringTag]);
    assertEquals(undefined, (new RangeError()).__proto__[Symbol.toStringTag]);
}

if (typeof ReferenceError === "function") {
    assertEquals("[object Function]", Object.prototype.toString.call(ReferenceError));
    assertEquals("[object Object]", Object.prototype.toString.call(ReferenceError.prototype));
    assertEquals("[object Error]", Object.prototype.toString.call(new ReferenceError()));
    assertEquals("[object Object]", Object.prototype.toString.call((new ReferenceError()).__proto__));
    assertEquals(undefined, ReferenceError.prototype[Symbol.toStringTag]);
    assertEquals(undefined, (new ReferenceError()).__proto__[Symbol.toStringTag]);
}

if (typeof SyntaxError === "function") {
    assertEquals("[object Function]", Object.prototype.toString.call(SyntaxError));
    assertEquals("[object Object]", Object.prototype.toString.call(SyntaxError.prototype));
    assertEquals("[object Error]", Object.prototype.toString.call(new SyntaxError()));
    assertEquals("[object Object]", Object.prototype.toString.call((new SyntaxError()).__proto__));
    assertEquals(undefined, SyntaxError.prototype[Symbol.toStringTag]);
    assertEquals(undefined, (new SyntaxError()).__proto__[Symbol.toStringTag]);
}

if (typeof TypeError === "function") {
    assertEquals("[object Function]", Object.prototype.toString.call(TypeError));
    assertEquals("[object Object]", Object.prototype.toString.call(TypeError.prototype));
    assertEquals("[object Error]", Object.prototype.toString.call(new TypeError()));
    assertEquals("[object Object]", Object.prototype.toString.call((new TypeError()).__proto__));
    assertEquals(undefined, TypeError.prototype[Symbol.toStringTag]);
    assertEquals(undefined, (new TypeError()).__proto__[Symbol.toStringTag]);
}

if (typeof URIError === "function") {
    assertEquals("[object Function]", Object.prototype.toString.call(URIError));
    assertEquals("[object Object]", Object.prototype.toString.call(URIError.prototype));
    assertEquals("[object Error]", Object.prototype.toString.call(new URIError()));
    assertEquals("[object Object]", Object.prototype.toString.call((new URIError()).__proto__));
    assertEquals(undefined, URIError.prototype[Symbol.toStringTag]);
    assertEquals(undefined, (new URIError()).__proto__[Symbol.toStringTag]);
}

if (typeof AggregateError === "function") {
    assertEquals("[object Function]", Object.prototype.toString.call(AggregateError));
    assertEquals("[object Object]", Object.prototype.toString.call(AggregateError.prototype));
    assertEquals("[object Error]", Object.prototype.toString.call(new AggregateError([])));
    assertEquals("[object Object]", Object.prototype.toString.call((new AggregateError([])).__proto__));
    assertEquals(undefined, AggregateError.prototype[Symbol.toStringTag]);
    assertEquals(undefined, (new AggregateError([])).__proto__[Symbol.toStringTag]);
}

// --- Date ---
assertEquals("[object Function]", Object.prototype.toString.call(Date));
assertEquals("[object Object]", Object.prototype.toString.call(Date.prototype));
assertEquals("[object Date]", Object.prototype.toString.call(new Date(0)));
assertEquals("[object Object]", Object.prototype.toString.call((new Date(0)).__proto__));
assertEquals(undefined, Date.prototype[Symbol.toStringTag]);
assertEquals(undefined, (new Date(0)).__proto__[Symbol.toStringTag]);

// --- RegExp ---
assertEquals("[object Function]", Object.prototype.toString.call(RegExp));
assertEquals("[object Object]", Object.prototype.toString.call(RegExp.prototype));
assertEquals("[object RegExp]", Object.prototype.toString.call(/a/));
assertEquals("[object Object]", Object.prototype.toString.call((/a/).__proto__));
assertEquals(undefined, RegExp.prototype[Symbol.toStringTag]);
assertEquals(undefined, (/a/).__proto__[Symbol.toStringTag]);

// --- Promise ---
assertEquals("[object Function]", Object.prototype.toString.call(Promise));
assertEquals("[object Promise]", Object.prototype.toString.call(Promise.prototype));
assertEquals("[object Promise]", Object.prototype.toString.call(Promise.resolve(1)));
assertEquals("[object Promise]", Object.prototype.toString.call(Promise.resolve(1).__proto__));
assertEquals("Promise", Promise.prototype[Symbol.toStringTag]);
assertEquals("Promise", Promise.resolve(1).__proto__[Symbol.toStringTag]);

// --- Map / Set / WeakMap / WeakSet ---
assertEquals("[object Function]", Object.prototype.toString.call(Map));
assertEquals("[object Map]", Object.prototype.toString.call(Map.prototype));
assertEquals("[object Map]", Object.prototype.toString.call(new Map()));
assertEquals("[object Map]", Object.prototype.toString.call((new Map()).__proto__));
assertEquals("Map", Map.prototype[Symbol.toStringTag]);
assertEquals("Map", (new Map()).__proto__[Symbol.toStringTag]);

assertEquals("[object Function]", Object.prototype.toString.call(Set));
assertEquals("[object Set]", Object.prototype.toString.call(Set.prototype));
assertEquals("[object Set]", Object.prototype.toString.call(new Set()));
assertEquals("[object Set]", Object.prototype.toString.call((new Set()).__proto__));
assertEquals("Set", Set.prototype[Symbol.toStringTag]);
assertEquals("Set", (new Set()).__proto__[Symbol.toStringTag]);

assertEquals("[object Function]", Object.prototype.toString.call(WeakMap));
assertEquals("[object WeakMap]", Object.prototype.toString.call(WeakMap.prototype));
assertEquals("[object WeakMap]", Object.prototype.toString.call(new WeakMap()));
assertEquals("[object WeakMap]", Object.prototype.toString.call((new WeakMap()).__proto__));
assertEquals("WeakMap", WeakMap.prototype[Symbol.toStringTag]);
assertEquals("WeakMap", (new WeakMap()).__proto__[Symbol.toStringTag]);

assertEquals("[object Function]", Object.prototype.toString.call(WeakSet));
assertEquals("[object WeakSet]", Object.prototype.toString.call(WeakSet.prototype));
assertEquals("[object WeakSet]", Object.prototype.toString.call(new WeakSet()));
assertEquals("[object WeakSet]", Object.prototype.toString.call((new WeakSet()).__proto__));
assertEquals("WeakSet", WeakSet.prototype[Symbol.toStringTag]);
assertEquals("WeakSet", (new WeakSet()).__proto__[Symbol.toStringTag]);

// --- ArrayBuffer / SharedArrayBuffer / DataView ---
assertEquals("[object Function]", Object.prototype.toString.call(ArrayBuffer));
assertEquals("[object ArrayBuffer]", Object.prototype.toString.call(ArrayBuffer.prototype));
assertEquals("[object ArrayBuffer]", Object.prototype.toString.call(new ArrayBuffer(0)));
assertEquals("[object ArrayBuffer]", Object.prototype.toString.call((new ArrayBuffer(0)).__proto__));
assertEquals("ArrayBuffer", ArrayBuffer.prototype[Symbol.toStringTag]);
assertEquals("ArrayBuffer", (new ArrayBuffer(0)).__proto__[Symbol.toStringTag]);

assertEquals("[object Function]", Object.prototype.toString.call(SharedArrayBuffer));
assertEquals("[object SharedArrayBuffer]", Object.prototype.toString.call(SharedArrayBuffer.prototype));
assertEquals("[object SharedArrayBuffer]", Object.prototype.toString.call(new SharedArrayBuffer(0)));
assertEquals("[object SharedArrayBuffer]", Object.prototype.toString.call((new SharedArrayBuffer(0)).__proto__));
assertEquals("SharedArrayBuffer", SharedArrayBuffer.prototype[Symbol.toStringTag]);
assertEquals("SharedArrayBuffer", (new SharedArrayBuffer(0)).__proto__[Symbol.toStringTag]);

assertEquals("[object Function]", Object.prototype.toString.call(DataView));
assertEquals("[object DataView]", Object.prototype.toString.call(DataView.prototype));
assertEquals("[object DataView]", Object.prototype.toString.call(new DataView(new ArrayBuffer(0))));
assertEquals("[object DataView]", Object.prototype.toString.call((new DataView(new ArrayBuffer(0))).__proto__));
assertEquals("DataView", DataView.prototype[Symbol.toStringTag]);
assertEquals("DataView", (new DataView(new ArrayBuffer(0))).__proto__[Symbol.toStringTag]);

// --- Typed arrays (optional per engine) ---
assertEquals("[object Function]", Object.prototype.toString.call(Int8Array));
assertEquals("[object Object]", Object.prototype.toString.call(Int8Array.prototype));
assertEquals("[object Int8Array]", Object.prototype.toString.call(new Int8Array(0)));
assertEquals("[object Object]", Object.prototype.toString.call((new Int8Array(0)).__proto__));
assertEquals(undefined, Int8Array.prototype[Symbol.toStringTag]);
assertEquals(undefined, (new Int8Array(0)).__proto__[Symbol.toStringTag]);

assertEquals("[object Function]", Object.prototype.toString.call(Uint8Array));
assertEquals("[object Object]", Object.prototype.toString.call(Uint8Array.prototype));
assertEquals("[object Uint8Array]", Object.prototype.toString.call(new Uint8Array(0)));
assertEquals("[object Object]", Object.prototype.toString.call((new Uint8Array(0)).__proto__));
assertEquals(undefined, Uint8Array.prototype[Symbol.toStringTag]);
assertEquals(undefined, (new Uint8Array(0)).__proto__[Symbol.toStringTag]);

assertEquals("[object Function]", Object.prototype.toString.call(Uint8ClampedArray));
assertEquals("[object Object]", Object.prototype.toString.call(Uint8ClampedArray.prototype));
assertEquals("[object Uint8ClampedArray]", Object.prototype.toString.call(new Uint8ClampedArray(0)));
assertEquals("[object Object]", Object.prototype.toString.call((new Uint8ClampedArray(0)).__proto__));
assertEquals(undefined, Uint8ClampedArray.prototype[Symbol.toStringTag]);
assertEquals(undefined, (new Uint8ClampedArray(0)).__proto__[Symbol.toStringTag]);

assertEquals("[object Function]", Object.prototype.toString.call(Int16Array));
assertEquals("[object Object]", Object.prototype.toString.call(Int16Array.prototype));
assertEquals("[object Int16Array]", Object.prototype.toString.call(new Int16Array(0)));
assertEquals("[object Object]", Object.prototype.toString.call((new Int16Array(0)).__proto__));
assertEquals(undefined, Int16Array.prototype[Symbol.toStringTag]);
assertEquals(undefined, (new Int16Array(0)).__proto__[Symbol.toStringTag]);

assertEquals("[object Function]", Object.prototype.toString.call(Uint16Array));
assertEquals("[object Object]", Object.prototype.toString.call(Uint16Array.prototype));
assertEquals("[object Uint16Array]", Object.prototype.toString.call(new Uint16Array(0)));
assertEquals("[object Object]", Object.prototype.toString.call((new Uint16Array(0)).__proto__));
assertEquals(undefined, Uint16Array.prototype[Symbol.toStringTag]);
assertEquals(undefined, (new Uint16Array(0)).__proto__[Symbol.toStringTag]);

assertEquals("[object Function]", Object.prototype.toString.call(Int32Array));
assertEquals("[object Object]", Object.prototype.toString.call(Int32Array.prototype));
assertEquals("[object Int32Array]", Object.prototype.toString.call(new Int32Array(0)));
assertEquals("[object Object]", Object.prototype.toString.call((new Int32Array(0)).__proto__));
assertEquals(undefined, Int32Array.prototype[Symbol.toStringTag]);
assertEquals(undefined, (new Int32Array(0)).__proto__[Symbol.toStringTag]);

assertEquals("[object Function]", Object.prototype.toString.call(Uint32Array));
assertEquals("[object Object]", Object.prototype.toString.call(Uint32Array.prototype));
assertEquals("[object Uint32Array]", Object.prototype.toString.call(new Uint32Array(0)));
assertEquals("[object Object]", Object.prototype.toString.call((new Uint32Array(0)).__proto__));
assertEquals(undefined, Uint32Array.prototype[Symbol.toStringTag]);
assertEquals(undefined, (new Uint32Array(0)).__proto__[Symbol.toStringTag]);

if (typeof Float16Array === "function") {
    assertEquals("[object Function]", Object.prototype.toString.call(Float16Array));
    assertEquals("[object Object]", Object.prototype.toString.call(Float16Array.prototype));
    assertEquals("[object Float16Array]", Object.prototype.toString.call(new Float16Array(0)));
    assertEquals("[object Object]", Object.prototype.toString.call((new Float16Array(0)).__proto__));
    assertEquals(undefined, Float16Array.prototype[Symbol.toStringTag]);
    assertEquals(undefined, (new Float16Array(0)).__proto__[Symbol.toStringTag]);
}

assertEquals("[object Function]", Object.prototype.toString.call(Float32Array));
assertEquals("[object Object]", Object.prototype.toString.call(Float32Array.prototype));
assertEquals("[object Float32Array]", Object.prototype.toString.call(new Float32Array(0)));
assertEquals("[object Object]", Object.prototype.toString.call((new Float32Array(0)).__proto__));
assertEquals(undefined, Float32Array.prototype[Symbol.toStringTag]);
assertEquals(undefined, (new Float32Array(0)).__proto__[Symbol.toStringTag]);

assertEquals("[object Function]", Object.prototype.toString.call(Float64Array));
assertEquals("[object Object]", Object.prototype.toString.call(Float64Array.prototype));
assertEquals("[object Float64Array]", Object.prototype.toString.call(new Float64Array(0)));
assertEquals("[object Object]", Object.prototype.toString.call((new Float64Array(0)).__proto__));
assertEquals(undefined, Float64Array.prototype[Symbol.toStringTag]);
assertEquals(undefined, (new Float64Array(0)).__proto__[Symbol.toStringTag]);

assertEquals("[object Function]", Object.prototype.toString.call(BigInt64Array));
assertEquals("[object Object]", Object.prototype.toString.call(BigInt64Array.prototype));
assertEquals("[object BigInt64Array]", Object.prototype.toString.call(new BigInt64Array(0)));
assertEquals("[object Object]", Object.prototype.toString.call((new BigInt64Array(0)).__proto__));
assertEquals(undefined, BigInt64Array.prototype[Symbol.toStringTag]);
assertEquals(undefined, (new BigInt64Array(0)).__proto__[Symbol.toStringTag]);

assertEquals("[object Function]", Object.prototype.toString.call(BigUint64Array));
assertEquals("[object Object]", Object.prototype.toString.call(BigUint64Array.prototype));
assertEquals("[object BigUint64Array]", Object.prototype.toString.call(new BigUint64Array(0)));
assertEquals("[object Object]", Object.prototype.toString.call((new BigUint64Array(0)).__proto__));
assertEquals(undefined, BigUint64Array.prototype[Symbol.toStringTag]);
assertEquals(undefined, (new BigUint64Array(0)).__proto__[Symbol.toStringTag]);

// --- Namespace objects (Math/JSON/Reflect/Atomics/Intl) ---
assertEquals("[object Math]", Object.prototype.toString.call(Math));
assertEquals("[object Object]", Object.prototype.toString.call(Math.__proto__));
assertEquals("Math", Math[Symbol.toStringTag]);
assertEquals(undefined, Math.__proto__[Symbol.toStringTag]); // Object.prototype => undefined

assertEquals("[object JSON]", Object.prototype.toString.call(JSON));
assertEquals("[object Object]", Object.prototype.toString.call(JSON.__proto__));
assertEquals("JSON", JSON[Symbol.toStringTag]);
assertEquals(undefined, JSON.__proto__[Symbol.toStringTag]);

assertEquals("[object Reflect]", Object.prototype.toString.call(Reflect));
assertEquals("[object Object]", Object.prototype.toString.call(Reflect.__proto__));
assertEquals("Reflect", Reflect[Symbol.toStringTag]);
assertEquals(undefined, Reflect.__proto__[Symbol.toStringTag]);

/*
assertEquals("[object Intl]", Object.prototype.toString.call(Intl));
assertEquals("[object Object]", Object.prototype.toString.call(Intl.__proto__));
assertEquals("Intl", Intl[Symbol.toStringTag]);
assertEquals(undefined, Intl.__proto__[Symbol.toStringTag]);
*/

// --- Global object (optional in some shells) ---
// No stable tag across environments; don't assert.
assertTrue(Object.prototype.toString.call(globalThis).indexOf("[object ") === 0);
// No stable @@toStringTag either; don't assert.
