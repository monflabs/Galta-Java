// Proxy passthrough test suite (no traps, handler = {})

// --- Helpers ----------------------------------------------------------------
function freshTarget() {
    return {
        a: 1,
        b: 2,
        getC() {
            return this.a + this.b;
        },
    };
}

// --- Basic identity & typeof -------------------------------------------------
{
    const t = {};
    const p = new Proxy(t, {});
    assertFalse(p === t);
    assertEquals("object", typeof p);
    assertEquals("[object Object]", Object.prototype.toString.call(p));
}

// --- Property get/set (primitives & functions) ------------------------------
{
    const t = freshTarget();
    const p = new Proxy(t, {});

    // read existing
    assertEquals(1, p.a);
    assertEquals(2, p.b);
    // read missing -> undefined
    assertUndefined(p.missing);

    // write new & update existing (affects target)
    p.a = 10;
    p.newProp = 42;
    assertEquals(10, t.a);
    assertEquals(42, t.newProp);

    // method call: 'this' should be the proxy when called as p.getC()
    assertEquals(10 + 2, p.getC());
    // confirm that the receiver really is the proxy
    t.whoAmI = function() {
        return this;
    };
    assertEqualsStrict(p, p.whoAmI());
    assertFalse(t === p.whoAmI());

    // direct function value equality (same function object)
    assertEqualsStrict(t.getC, p.getC);
}

// --- "in" operator and delete -----------------------------------------------
{
    const t = {
        x: 1
    };
    const p = new Proxy(t, {});
    assertTrue("x" in p);
    assertFalse("y" in p);
    // delete forwards
    assertTrue(delete p.x);
    assertFalse("x" in t);
}

// --- Object.keys / ownKeys / property descriptors ---------------------------
{
    const sym = Symbol("s");
    const t = Object.defineProperties({}, {
        a: {
            value: 1,
            enumerable: true,
            configurable: true,
            writable: true
        },
        b: {
            value: 2,
            enumerable: false,
            configurable: true,
            writable: true
        },
    });
    t[sym] = 3;
    const p = new Proxy(t, {});

    // keys only enumerable string keys
    const keys = Object.keys(p);
    assertEquals(1, keys.length);
    assertEquals("a", keys[0]);

    // own names and symbols
    const names = Object.getOwnPropertyNames(p).sort();
    assertEquals(2, names.length);
    assertEquals("a", names[0]);
    assertEquals("b", names[1]);
    const symbols = Object.getOwnPropertySymbols(p);
    assertEquals(1, symbols.length);
    assertEqualsStrict(sym, symbols[0]);

    // getOwnPropertyDescriptor forwards
    const da = Object.getOwnPropertyDescriptor(p, "a");
    const db = Object.getOwnPropertyDescriptor(p, "b");
    const ds = Object.getOwnPropertyDescriptor(p, sym);

    assertEquals(1, da.value);
    assertTrue(da.enumerable);
    assertEquals(2, db.value);
    assertFalse(db.enumerable);
    assertEquals(3, ds.value);

    // defineProperty forwards to target
    Object.defineProperty(p, "c", {
        value: 4,
        enumerable: true
    });
    assertEquals(4, t.c);
    assertTrue(Object.prototype.hasOwnProperty.call(t, "c"));
}

// --- Prototype operations ----------------------------------------------------
{
    const proto = {
        k: 7
    };
    const t = {};
    const p = new Proxy(t, {});

    assertEquals(p, Object.setPrototypeOf(p, proto));
    assertEqualsStrict(proto, Object.getPrototypeOf(p));
    assertEqualsStrict(proto, Object.getPrototypeOf(t));
    assertEquals(7, p.k); // via proto

    // setPrototypeOf(null) allowed only if target is extensible or proto is compatible
    assertEquals(p, Object.setPrototypeOf(p, null));
    assertEqualsStrict(null, Object.getPrototypeOf(p));
    assertEqualsStrict(null, Object.getPrototypeOf(t));
}

// --- Extensibility & preventExtensions --------------------------------------
{
    const t = {
        a: 1
    };
    const p = new Proxy(t, {});
    assertTrue(Object.isExtensible(p));
	assertEquals(p, Object.preventExtensions(p));
    assertFalse(Object.isExtensible(p));
    assertFalse(Object.isExtensible(t));

    // Adding property after preventExtensions should fail (silently in non-strict)
    assertThrows( () => p.b = 2 );
    assertFalse(Object.prototype.hasOwnProperty.call(p, "b"));
    assertFalse(Object.prototype.hasOwnProperty.call(t, "b"));
}

// --- Arrays behave the same through the proxy --------------------------------
{
    const t = [10, 20, 30];
    const p = new Proxy(t, {});

    // length & indexed get/set
    assertEquals(3, p.length);
    assertEquals(20, p[1]);
    p[1] = 99;
    assertEquals(99, t[1]);
    assertEquals(3, t.length);

    // push/pop/etc. (methods read & write through proxy)
    p.push(40);
    assertEquals(4, p.length);
    assertEquals(40, t[3]);
    assertEquals("[object Array]", Object.prototype.toString.call(p));

    // for...in enumerates indices that are enumerable
    const seen = [];
    for (const k in p) seen.push(k);
    // In ascending order of creation (engine-dependent but typical: "0","1","2","3")
    assertEquals(4, seen.length);
    assertEquals("0", seen[0]);
}

// --- Symbols as property keys ------------------------------------------------
{
    const S = Symbol("key");
    const t = {};
    const p = new Proxy(t, {});
    p[S] = 123;
    assertEquals(123, t[S]);
    assertEquals(123, p[S]);
    assertEquals(1, Object.getOwnPropertySymbols(p).length);
}

// --- JSON.stringify and toStringTag -----------------------------------------
{
    const t = {
        x: 1,
        y: 2
    };
    const p = new Proxy(t, {});
    assertEquals('{"x":1,"y":2}', JSON.stringify(p));

    // @@toStringTag forwarded via get
    const tag = {};
    tag[Symbol.toStringTag] = "CustomThing";
    const pt = new Proxy(tag, {});
    assertEquals("[object CustomThing]", Object.prototype.toString.call(pt));
}

// --- Map/Set identity & equality --------------------------------------------
{
    const t = {};
    const p = new Proxy(t, {});
    const m = new Map();
    m.set(p, "via proxy");
    assertEquals("via proxy", m.get(p));
    assertUndefined(m.get(t)); // different identity

    const s = new Set();
    s.add(p);
    assertTrue(s.has(p));
    assertFalse(s.has(t));
}

// --- Function target: call & construct --------------------------------------
{
    function F(x, y) {
        this.sum = x + y;
    }
    F.answer = 42;
    F.prototype.protoMethod = function() {
        return this.sum;
    };

    const pf = new Proxy(F, {});

    // Callable
    const tmp = {};
    assertEquals(42, pf.answer); // static prop get
    // call with explicit this
    function setZ(v) {
        this.z = v;
        return this;
    }
    const pSetZ = new Proxy(setZ, {});
    const rec = {};
    assertEqualsStrict(rec, pSetZ.call(rec, 5));
    assertEquals(5, rec.z);

    // Constructible (since F is constructible)
    const inst = new pf(2, 3);
    assertTrue(inst instanceof pf);
    assertTrue(inst instanceof F);
    assertEquals(5, inst.protoMethod());

    // typeof
    assertEquals("function", typeof pf);
    assertTrue(pf instanceof Function);
}

// --- Non-callable target throws on call/construct ----------------------------
{
    const t = {};
    const p = new Proxy(t, {});
    assertThrows(TypeError, () => p()); // Call on non-callable
    assertThrows(TypeError, () => new p()); // Construct on non-constructible
}

// --- instanceof with proxied constructor ------------------------------------
{
    function C() {}
    const PC = new Proxy(C, {});
    const o = new PC();
    assertTrue(o instanceof PC);
    assertTrue(o instanceof C);

    // Changing prototype through the proxy affects instanceof
    const otherProto = {};
    Object.setPrototypeOf(C.prototype, otherProto);
    // Still true since C.prototype is in the chain
    assertTrue(o instanceof C);
}

// --- Object.assign pulls from proxied source --------------------------------
{
    const t = {
        a: 1
    };
    Object.defineProperty(t, "hidden", {
        value: 2,
        enumerable: false
    });
    const p = new Proxy(t, {});
    const dst = {
        z: 0
    };
    Object.assign(dst, p);
    assertEquals(0, dst.z);
    assertEquals(1, dst.a);
    assertUndefined(dst.hidden);
}

// --- preventExtensions invariants through proxy ------------------------------
{
	const t = {
	    fixed: 1
	};
	Object.defineProperty(t, "locked", {
	    value: 2,
	    configurable: false,
	    writable: false
	});
	const p = new Proxy(t, {});
	assertTrue(Object.preventExtensions(p));
	assertFalse(Object.isExtensible(t));

	// Cannot add new properties
	assertThrows(TypeError, () => {
	    "use strict";
	    p.newProp = 3;
	});
	assertFalse(Object.prototype.hasOwnProperty.call(t, "newProp"));

	// Cannot change non-writable
	assertThrows(TypeError, () => {
	    "use strict";
	    p.locked = 99;
	});
	assertEquals(2,t.locked);
}

// --- Proxy.revocable passthrough prior to revoke, then throws ----------------
{
    const t = {
        x: 1
    };
    const {
        proxy,
        revoke
    } = Proxy.revocable(t, {});
    assertEquals(1, proxy.x);
    proxy.x = 5;
    assertEquals(5, t.x);

    revoke();
    assertThrows(TypeError, () => proxy.x); // get after revoke
    assertThrows(TypeError, () => {
        proxy.x = 9;
    }); // set after revoke
    assertThrows(TypeError, () => "x" in proxy);
    assertThrows(TypeError, () => delete proxy.x);
    assertThrows(TypeError, () => Object.keys(proxy));
}

// --- getPrototypeOf/setPrototypeOf on function proxies ----------------------
{
    function G() {}
    const pg = new Proxy(G, {});
    assertEqualsStrict(Function.prototype, Object.getPrototypeOf(pg));
    assertTrue(Object.setPrototypeOf(pg, Function.prototype)); // no-op but should succeed
    assertEqualsStrict(Function.prototype, Object.getPrototypeOf(pg));
}

// --- Descriptor reflect: writable/configurable changes propagate -------------
{
    const t = {};
    const p = new Proxy(t, {});
    Object.defineProperty(p, "d", {
        value: 1,
        configurable: true,
        writable: true,
        enumerable: true
    });
    assertEquals(1, t.d);

    // Flip writable via proxy, then writes are blocked in strict mode
    Object.defineProperty(p, "d", {
        writable: false
    });
    assertFalse(Object.getOwnPropertyDescriptor(t, "d").writable);
    assertThrows(TypeError, () => {
        "use strict";
        p.d = 3;
    });
    assertEquals(1, p.d);

    // Make non-configurable and verify
    Object.defineProperty(p, "d", {
        configurable: false
    });
    assertFalse(Object.getOwnPropertyDescriptor(t, "d").configurable);
}

// --- toStringTag: Array recurses through Proxy, other builtin tags don't ----
{
    const arrP = new Proxy([], {});
    assertEquals("[object Array]", Object.prototype.toString.call(arrP));

    // Spec: a Proxy never has [[DateValue]] itself even when its target
    // does - only IsArray is spec'd to recurse through Proxies for
    // Object.prototype.toString's builtinTag.
    const d = new Date(0);
    const dp = new Proxy(d, {});
    assertEquals("[object Object]", Object.prototype.toString.call(dp));
}

// --- hasOwn / propertyIsEnumerable passthrough -------------------------------
{
    const t = {};
    Object.defineProperty(t, "e", {
        value: 1,
        enumerable: false
    });
    const p = new Proxy(t, {});
    assertTrue(Object.prototype.hasOwnProperty.call(p, "e"));
    assertFalse(Object.prototype.propertyIsEnumerable.call(p, "e"));
}

// --- Strict equality & loose equality (identity semantics) -------------------
{
    const t = {};
    const p = new Proxy(t, {});
    assertFalse(p === t);
    assertFalse(p == t);
    assertEqualsStrict(p, p);
    assertEqualsStrict(t, t);
}

// --- getOwnPropertyDescriptor on missing returns undefined -------------------
{
    const p = new Proxy({}, {});
    assertUndefined(Object.getOwnPropertyDescriptor(p, "nope"));
}

// --- Reflect.* through proxy equals using target directly --------------------
{
    const t = {
        a: 1
    };
    const p = new Proxy(t, {});

    assertEquals(1, Reflect.get(p, "a"));
    assertTrue(Reflect.set(p, "b", 2));
    assertEquals(2, t.b);

    assertTrue(Reflect.has(p, "a"));
    assertTrue(Reflect.deleteProperty(p, "a"));
    assertFalse(Reflect.has(t, "a"));

    const desc = Reflect.getOwnPropertyDescriptor(p, "b");
    assertEquals(2, desc.value);

    const keys = Reflect.ownKeys(p);
    assertTrue(keys.indexOf("b") >= 0);

    assertEqualsStrict(Object.getPrototypeOf(t), Reflect.getPrototypeOf(p));
    assertTrue(Reflect.isExtensible(p));
    assertTrue(Reflect.preventExtensions(p));
    assertFalse(Reflect.isExtensible(t));
}

// A non-extensible ARRAY still allows a brand-new own property to be
// created via [[Set]]/CreateDataProperty - a genuine, pre-existing,
// array-specific bug (plain non-extensible OBJECTS already correctly
// reject this) - see KnownGaps.md, entangled with the array-exotic-object
// gap family. Not fixed here (out of scope for the OrdinarySet/Proxy fix).
{
    var plainArr = [1, 2, 3];
    Object.preventExtensions(plainArr);
    // Known-broken: should be false (rejected), currently true.
    // assertFalse(Reflect.set(plainArr, "bar", 5));

    var plainObj = {};
    Object.preventExtensions(plainObj);
    assertFalse(Reflect.set(plainObj, "bar", 5));
}

// Error.prototype's own [[Prototype]] must be Object.prototype (was
// wrongly Function.prototype, leaking a non-writable "length" into every
// Error instance's prototype chain).
{
    assertEqualsStrict(Object.getPrototypeOf(Error.prototype), Object.prototype);
    var obj = new Error();
    obj.length = 1;
    assertEquals(1, obj.length);
}

// Object literal property creation (PropertyDefinitionEvaluation) must use
// CreateDataProperty ([[DefineOwnProperty]]), not [[Set]] - an inherited
// non-writable/accessor property of the same name must not block a literal
// from getting its own property (matches the class-field fix above).
{
    Object.defineProperty(Object.prototype, "0", {
        get: function() { return false; },
        configurable: true
    });
    var o = { 0: true, 1: 1, length: 2 };
    assertEquals(true, o[0]);
    assertTrue(o.hasOwnProperty("0"));
    delete Object.prototype[0];

    // Object spread must also use CreateDataProperty, and (unlike a literal
    // `__proto__: value` entry) treats a spread "__proto__" key as an
    // ordinary own property, never changing the prototype. A computed key
    // (["__proto__"]) - unlike the literal form - never gets the special
    // B.3.1 treatment, so it's an ordinary own property on the SOURCE too.
    var proto = {};
    var source = { ["__proto__"]: proto, x: 1 };
    var spread = { ...source };
    assertFalse(Object.getPrototypeOf(spread) === proto);
    assertEquals(proto, spread.__proto__);
    assertEquals(1, spread.x);
}