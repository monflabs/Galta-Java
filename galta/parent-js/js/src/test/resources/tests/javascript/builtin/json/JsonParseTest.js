// Test file for JSON.parse
function test_JSON_parse() {
    // Basic parsing tests
    {
        assertEquals(123, JSON.parse('123'));
        assertEquals('abc', JSON.parse('"abc"'));
        assertEquals(true, JSON.parse('true'));
        assertEquals(false, JSON.parse('false'));
        assertEquals(null, JSON.parse('null'));
    }

    // Parsing objects
    {
        const obj = JSON.parse('{"a":1,"b":2}');
        assertEquals(1, obj.a);
        assertEquals(2, obj.b);
        assertTrue(typeof obj === 'object');
        assertFalse(Array.isArray(obj));
    }

    // Parsing arrays
    {
        const arr = JSON.parse('[1,2,3]');
        assertEquals(3, arr.length);
        assertEquals(1, arr[0]);
        assertEquals(2, arr[1]);
        assertEquals(3, arr[2]);
        assertTrue(Array.isArray(arr));
    }

    // Parsing nested structures
    {
        const data = JSON.parse('{"x":[{"y":true}]}');
        assertTrue(Array.isArray(data.x));
        assertTrue(typeof data.x[0] === 'object');
        assertEquals(true, data.x[0].y);
    }

    // Parsing empty object and array
    {
        assertEquals(0, Object.keys(JSON.parse('{}')).length);
        assertEquals(0, JSON.parse('[]').length);
    }

    // Whitespace tolerance
    {
        assertEquals(123, JSON.parse('   123   '));
        assertEquals(null, JSON.parse('\n\nnull\t'));
    }

    // Reviver function: basic behavior
    {
        const revived = JSON.parse('{"a":1,"b":2}', (key, value) => {
            if (key === 'a') return value + 10;
            return value;
        });
        assertEquals(11, revived.a);
        assertEquals(2, revived.b);
    }

    // Reviver changing structure
    {
        const revived = JSON.parse('{"a":{"b":1}}', (key, value) => {
            if (key === 'b') return undefined; // Remove key 'b'
            return value;
        });
        assertEquals(undefined, revived.a.b);
    }

    // Reviver receives correct types
    {
        JSON.parse('{"a":1,"b":[2,3]}', (key, value) => {
            if (key === 'b') {
                assertTrue(Array.isArray(value));
            }
            if (key === '0' || key === '1') {
                assertTrue(typeof value === 'number');
            }
            return value;
        });
    }

    // Reviver root object transformation
    {
        const result = JSON.parse('123', (key, value) => value + 1);
        assertEquals(124, result);
    }

    // Reviver 3rd argument: a plain object whose "source" property only
    // exists for a primitive-literal value, not for an object/array container.
    {
        let seenContext;
        JSON.parse('42', (key, value, context) => { seenContext = context; return value; });
        assertTrue(typeof seenContext === 'object');
        assertEquals(Object.prototype, Object.getPrototypeOf(seenContext));
        assertEquals('42', seenContext.source);

        let containerContext;
        JSON.parse('{"a":1}', (key, value, context) => {
            if (key === '') containerContext = context;
            return value;
        });
        assertEquals(false, 'source' in containerContext);
    }

    // Error: invalid JSON
    {
        assertThrows(SyntaxError, () => JSON.parse('{'));
        assertThrows(SyntaxError, () => JSON.parse('['));
        assertThrows(SyntaxError, () => JSON.parse('true false'));
        assertThrows(SyntaxError, () => JSON.parse('{"a":1,}'));
        assertThrows(SyntaxError, () => JSON.parse('{123}'));
    }

    // Error: non-string input
    {
        assertThrows(SyntaxError, () => JSON.parse(undefined));
        assertEquals(null, JSON.parse(null));
        assertEquals(123, JSON.parse(123));
        assertEquals(true, JSON.parse(true));
        assertThrows(SyntaxError, () => JSON.parse({}));
        assertThrows(SyntaxError, () => JSON.parse([]));
    }

    // Reviver: exceptions are thrown correctly
    {
        assertThrows(Error, () => {
            JSON.parse('{"a":1}', (key, value) => {
                if (key === 'a') {
                    throw new Error('Custom error');
                }
                return value;
            });
        });
    }

    // Reviver: root key is empty string
    {
        JSON.parse('123', (key, value) => {
            assertEquals('', key); // First call to reviver is with empty key
            return value;
        });
    }

    // Reviver: InternalizeJSONProperty is a SEPARATE, post-parse, live walk -
    // an earlier sibling's reviver call forward-modifying a not-yet-visited
    // position must be observed when that position is later visited (not
    // read straight from the original parsed text).
    {
        const calls = [];
        const result = JSON.parse('[1, 2]', function (key, value) {
            calls.push(key);
            if (key === '0') {
                this[1] = 42;
            }
            return this[key];
        });
        assertEquals(['0', '1', ''], calls);
        assertEquals([1, 42], result);
    }

    // Reviver: forward-modifying with a nested object/array recurses into it
    // (the walk re-checks IsArray/enumerates own keys on the LIVE value).
    {
        const calls = [];
        const result = JSON.parse('[1, 2]', function (key, value) {
            calls.push(key);
            if (key === '0') {
                this[1] = { x: 'y' };
            }
            return this[key];
        });
        assertEquals(['0', 'x', '1', ''], calls);
        assertEquals(1, result[0]);
        assertEquals('y', result[1].x);
    }

    // Reviver: own enumerable keys are visited in [[OwnPropertyKeys]] order
    // (integer indices ascending, then string keys in insertion order) - not
    // the order the source text happened to declare them in.
    {
        const calls = [];
        JSON.parse('{"b":1,"2":2,"a":3,"1":4}', function (key, value) {
            calls.push(key);
            return value;
        });
        assertEquals(['1', '2', 'b', 'a', ''], calls);
    }

    // Reviver: reviving into a Proxy wrapping a plain object is treated as an
    // ordinary object for the walk (recurses into its own keys); a Proxy
    // wrapping an array is treated as array-form (recurses by index, not by
    // enumerating own string keys like "other").
    {
        const arrayProxy = new Proxy([], {});
        arrayProxy.other = 'x';
        const calls = [];
        JSON.parse('[null, null]', function (key, value) {
            calls.push(key);
            if (key === '0') {
                this[1] = arrayProxy;
            }
            return value;
        });
        assertFalse(calls.includes('other'));
    }

    // Reviver: context.source is only present for a primitive literal value,
    // never for a container.
    {
        const sources = [];
        JSON.parse('[1, {"a": 2}]', function (key, value, context) {
            sources.push(context.source);
            return value;
        });
        assertEquals(['1', '2', undefined, undefined], sources);
    }

    // Big JSON (stress test)
    {
        const arr = Array(1000).fill(0).map((_, i) => i);
        const jsonString = JSON.stringify(arr);
        const parsed = JSON.parse(jsonString);
        assertEquals(arr.length, parsed.length);
        assertEquals(arr[123], parsed[123]);
    }

    // Unicode characters
    {
        const obj = JSON.parse('{"char":"\\u2764"}'); // Unicode heart
        assertEquals('❤', obj.char);
    }

    // Escaped characters
    {
        const obj = JSON.parse('{"newline":"Line1\\nLine2"}');
        assertEquals('Line1\nLine2', obj.newline);
    }
}

test_JSON_parse();

// The SyntaxError message says what is wrong and where
{
    function messageOf(f) {
        try {
            f();
        } catch (e) {
            assertTrue(e instanceof SyntaxError);
            return e.message;
        }
        throw new Error('No exception');
    }
    let m = messageOf(() => JSON.parse('{"a":}'));
    assertTrue(m.indexOf("Unexpected character '}'") >= 0);
    assertTrue(m.indexOf('at position 5') >= 0);
    m = messageOf(() => JSON.parse('[1, 2'));
    assertTrue(m.indexOf('Unexpected end of input at position 5') >= 0);
    m = messageOf(() => JSON.parse('[1, 2, ]'));
    assertTrue(m.indexOf('A trailing comma is not allowed in strict mode, at position 7') >= 0);
    m = messageOf(() => JSON.parse('{a: 1}'));
    assertTrue(m.indexOf('An unquoted key') >= 0);
    // One line, without the source excerpt
    assertEquals(-1, messageOf(() => JSON.parse('[\n1,\n]')).indexOf('\n'));
}

// Standard JSON only
{
    assertThrows(SyntaxError, () => JSON.parse('\uFEFF[1]'));    // a BOM is not whitespace
    assertThrows(SyntaxError, () => JSON.parse(''));
    assertThrows(SyntaxError, () => JSON.parse(' '));
    assertThrows(SyntaxError, () => JSON.parse('0x10'));
    assertThrows(SyntaxError, () => JSON.parse('NaN'));
    assertThrows(SyntaxError, () => JSON.parse('1 /* c */'));
    assertThrows(SyntaxError, () => JSON.parse("'a'"));
}

// Numbers: no length limit, exponents out of range are Infinity and 0
{
    assertEquals(Infinity, JSON.parse('1e3000000000'));
    assertEquals(-Infinity, JSON.parse('-1e3000000000'));
    assertEquals(0, JSON.parse('1e-3000000000'));
    assertEquals(Infinity, JSON.parse('1'.repeat(5000)));
    assertEquals(1.5, JSON.parse('1.5' + '0'.repeat(5000)));
    assertEquals(1e300, JSON.parse('1' + '0'.repeat(300)));
}

// Nesting too deep is a SyntaxError, not a Java exception
{
    assertThrows(SyntaxError, () => JSON.parse('['.repeat(100000) + ']'.repeat(100000)));
}
