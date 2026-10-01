// Test file for JSON.stringify
function test_JSON_stringify() {
    const obj = {
        a1: { b1: [1, 2, 3, 4], b2: { c1: 1, c2: 2 } },
        a2: 'a2',
    };

    // space: Number is clamped to [0,10] spaces; String is truncated to 10 chars;
    // an empty gap (0 or '') must be fully compact, identical to no space at all.
    {
        assertEquals(JSON.stringify(obj), JSON.stringify(obj, null, 0));
        assertEquals(JSON.stringify(obj), JSON.stringify(obj, null, ''));
        assertEquals(JSON.stringify(obj, null, '    '), JSON.stringify(obj, null, 4));
        assertEquals(JSON.stringify(obj, null, 10), JSON.stringify(obj, null, 20));
        assertEquals(JSON.stringify(obj, null, '0123456789'), JSON.stringify(obj, null, '0123456789xxxxxxxxx'));
        // pretty-print puts a space after ":"
        assertEquals('{\n  "a": 1\n}', JSON.stringify({ a: 1 }, null, 2));
    }

    // BigInt has no default JSON representation: throws unless toJSON exists.
    {
        assertThrows(TypeError, () => JSON.stringify(0n));
        assertThrows(TypeError, () => JSON.stringify({ x: 0n }));
        BigInt.prototype.toJSON = function () { return this.toString(); };
        try {
            assertEquals('"0"', JSON.stringify(0n));
        } finally {
            delete BigInt.prototype.toJSON;
        }
    }

    // toJSON's result is re-serialized, not returned as-is: an undefined
    // result must be treated as JS undefined (omitted/null), not the text "undefined".
    {
        assertEquals(undefined, JSON.stringify({ toJSON() {} }));
        const arr = [true];
        arr.toJSON = function () {};
        assertEquals(undefined, JSON.stringify(arr));
        assertEquals('{"key":null}', JSON.stringify({ key: { toJSON: () => null } }));
    }

    // Circular references throw TypeError (not a raw StackOverflowError from
    // the error message construction recursing into the circular structure itself).
    {
        const direct = [];
        direct.push(direct);
        assertThrows(TypeError, () => JSON.stringify(direct));

        const obj2 = {};
        obj2.self = obj2;
        assertThrows(TypeError, () => JSON.stringify(obj2));
    }

    // JSON.stringify snapshots the property key list BEFORE enumeration
    // begins - a getter's own side effect of adding a NEW property to the
    // same object must not be observable in this same stringify pass, and
    // non-enumerable properties must never appear in the output at all.
    {
        const obj = { a: 1 };
        Object.defineProperty(obj, 'trigger', {
            enumerable: true,
            get() {
                obj.newProp = 'leaked';
                return 'triggerValue';
            }
        });
        assertEquals('{"a":1,"trigger":"triggerValue"}', JSON.stringify(obj));

        const obj2 = { a: 1 };
        Object.defineProperty(obj2, 'hidden', { value: 'secret', enumerable: false });
        assertEquals('{"a":1}', JSON.stringify(obj2));
    }
}

test_JSON_stringify();

// Array-form replacer: builds a real, ordered, de-duped PropertyList up
// front - drives BOTH which keys are included AND their output order
// (not the object's own key order), for every plain object in the tree.
{
    // Order comes from the replacer array, not from the object's own keys.
    assertEquals('{"c":3,"b":1,"a":2}', JSON.stringify({b: 1, a: 2, c: 3}, ['c', 'b', 'a']));
    assertEquals('{"a":{"c":3,"b":2}}', JSON.stringify({a: {b: 2, c: 3}}, ['c', 'b', 'a']));

    // De-duplicated: a getter for a key listed twice is only read once.
    let getCalls = 0;
    const value = { get key() { getCalls++; return true; } };
    assertEquals('{"key":true}', JSON.stringify(value, ['key', 'key']));
    assertEquals(1, getCalls);

    // Empty replacer array -> every object serializes as {}.
    assertEquals('{}', JSON.stringify({a: 1, b: 2}, []));
    assertEquals('[1,{}]', JSON.stringify([1, {a: 2}], []));

    // Boxed Number/String wrapper elements are ToString-coerced (consulting
    // an overridden toString, never valueOf, per spec's hint="string" order).
    const num = new Number(10);
    num.toString = function() { return 'toString'; };
    num.valueOf = function() { throw new Error('should not be called'); };
    assertEquals('{"toString":2}', JSON.stringify({10: 1, toString: 2, valueOf: 3}, [num]));

    const str = new String('str');
    str.toString = function() { return 'toString'; };
    str.valueOf = function() { throw new Error('should not be called'); };
    assertEquals('{"toString":2}', JSON.stringify({str: 1, toString: 2, valueOf: 3}, [str]));

    // A Proxy wrapping a real array is still treated as array-form (IsArray
    // unwraps through the Proxy to its target).
    const proxyReplacer = new Proxy(['b'], {});
    assertEquals('{"b":2}', JSON.stringify({a: 1, b: 2}, proxyReplacer));
    assertEquals('{"b":{"b":4}}', JSON.stringify({b: {a: 3, b: 4}}, proxyReplacer));

    // A Proxy wrapping a non-callable target (like an array) must not be
    // mistaken for a function replacer just because Proxy objects are
    // always `instanceof Callable` at the Java level - and a throwing
    // "length"/index trap on the replacer array must propagate.
    assertEquals('null', JSON.stringify(null, new Proxy([], {})));

    const abruptLength = new Proxy([], {
        get(_target, key) {
            if (key === 'length') { throw new Error('abrupt-length'); }
        }
    });
    assertThrows(Error, () => JSON.stringify(null, abruptLength));
}

// QuoteJSONString: only the control characters, '"', '\\' and the lone surrogates are
// escaped; every other character, non ASCII included, is written as is.
{
    assertEquals('"é"', JSON.stringify('é'));
    assertEquals(3, JSON.stringify('é').length);
    assertEquals('"日本語"', JSON.stringify('日本語'));
    assertEquals(5, JSON.stringify('日本語').length);
    // U+2028 and U+2029 are written raw (unlike JavaScript source, JSON allows them)
    assertEquals('"\u2028\u2029"', JSON.stringify('\u2028\u2029'));
    assertEquals(4, JSON.stringify('\u2028\u2029').length);
    assertEquals('"\u007f"', JSON.stringify('\u007f'));
    // A surrogate pair is written as is, a lone surrogate is escaped
    assertEquals('"😀"', JSON.stringify('😀'));
    assertEquals('"\\ud834"', JSON.stringify('\uD834'));
    assertEquals('"\\udf06"', JSON.stringify('\uDF06'));
    assertEquals('"\\ud834𝌆\\ud834"', JSON.stringify('\uD834𝌆\uD834'));
    // Controls are escaped, lowercase hexadecimal
    assertEquals('"\\u0001\\u001f\\b\\t\\n\\f\\r\\"\\\\"', JSON.stringify('\u0001\u001f\b\t\n\f\r"\\'));
    // Keys too, and the round trip
    assertEquals('{"clé":"été"}', JSON.stringify({ 'clé': 'été' }));
    const s = 'aé日😀\u2028\u0001"\\';
    assertEquals(s, JSON.parse(JSON.stringify(s)));
}

// A value nested too deeply is a RangeError (like V8's "Maximum call stack size exceeded"),
// not a Java exception
{
    let deep = [];
    for (let i = 0; i < 20000; i++) {
        deep = [deep];
    }
    assertThrows(RangeError, () => JSON.stringify(deep));
    let deepObject = {};
    for (let i = 0; i < 20000; i++) {
        deepObject = { a: deepObject };
    }
    assertThrows(RangeError, () => JSON.stringify(deepObject));
    // A reasonable depth is fine
    let ok = [];
    for (let i = 0; i < 100; i++) {
        ok = [ok];
    }
    assertEquals('['.repeat(101) + ']'.repeat(101), JSON.stringify(ok));
}
