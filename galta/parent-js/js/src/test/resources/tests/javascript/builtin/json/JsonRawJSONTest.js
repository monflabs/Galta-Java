// Corrected test file for JSON.rawJSON

function test_JSON_rawJSON() {
    // ✅ Valid primitive JSON strings
    {
        const raw1 = JSON.rawJSON('"text"');
        const raw2 = JSON.rawJSON('123');
        const raw3 = JSON.rawJSON('true');
        const raw4 = JSON.rawJSON('false');
        const raw5 = JSON.rawJSON('null');

        assertTrue(JSON.isRawJSON(raw1));
        assertTrue(JSON.isRawJSON(raw2));
        assertTrue(JSON.isRawJSON(raw3));
        assertTrue(JSON.isRawJSON(raw4));
        assertTrue(JSON.isRawJSON(raw5));

        assertEquals('{"a":"text"}', JSON.stringify({ a: raw1 }));
        assertEquals('{"a":123}', JSON.stringify({ a: raw2 }));
        assertEquals('{"a":true}', JSON.stringify({ a: raw3 }));
        assertEquals('{"a":false}', JSON.stringify({ a: raw4 }));
        assertEquals('{"a":null}', JSON.stringify({ a: raw5 }));
    }
    // ❌ Invalid JSON strings representing structured values
    {
        assertThrows(SyntaxError, () => JSON.rawJSON('[1,2,3]'));
        assertThrows(SyntaxError, () => JSON.rawJSON('{"x":1}'));
    }

    // ❌ Malformed JSON strings
    {
        assertThrows(SyntaxError, () => JSON.rawJSON(''));
        assertThrows(SyntaxError, () => JSON.rawJSON('text'));           // unquoted
        assertThrows(SyntaxError, () => JSON.rawJSON('{x:1}'));          // unquoted key
        assertThrows(SyntaxError, () => JSON.rawJSON('[1,2,]'));         // trailing comma
        assertThrows(SyntaxError, () => JSON.rawJSON('true false'));     // multiple values
    }

    // ❌ Non-string input
    {
        assertEquals("123", JSON.stringify(JSON.rawJSON(123)));
        assertEquals("null", JSON.stringify(JSON.rawJSON(null)));
        assertThrows(SyntaxError, () => JSON.rawJSON({}));
        assertThrows(SyntaxError, () => JSON.rawJSON([]));
        assertThrows(SyntaxError, () => JSON.rawJSON(undefined));
        assertThrows(TypeError, () => JSON.rawJSON(Symbol('x')));
        assertThrows(SyntaxError, () => JSON.rawJSON(() => {}));
    }

    // 🧪 Unique instance per call
    {
        const r1 = JSON.rawJSON('"a"');
        const r2 = JSON.rawJSON('"a"');
        assertFalse(r1 === r2);
    }

    // ✅ Works in array contexts
    {
        const raw = JSON.rawJSON('123');
        assertEquals('[0,123,2]', JSON.stringify([0, raw, 2]));
    }

    // ✅ Used with reviver pattern
    {
        const input = '{"payload":"true"}';
        const parsed = JSON.parse(input, (key, value) => {
            if (key === 'payload') return JSON.rawJSON(value);
            return value;
        });
        assertTrue(JSON.isRawJSON(parsed.payload));
        assertEquals('{"payload":true}', JSON.stringify(parsed));
    }
}

test_JSON_rawJSON();

// Standard JSON only, like JSON.parse: the lenient syntax is a SyntaxError
{
    assertThrows(SyntaxError, () => JSON.rawJSON('0x10'));
    assertThrows(SyntaxError, () => JSON.rawJSON('NaN'));
    assertThrows(SyntaxError, () => JSON.rawJSON('Infinity'));
    assertThrows(SyntaxError, () => JSON.rawJSON('1/*c*/'));
    assertThrows(SyntaxError, () => JSON.rawJSON("'a'"));
    assertThrows(SyntaxError, () => JSON.rawJSON('+1'));
    assertThrows(SyntaxError, () => JSON.rawJSON('.5'));
    assertThrows(SyntaxError, () => JSON.rawJSON('01'));
    assertThrows(SyntaxError, () => JSON.rawJSON('"\\x41"'));
    assertThrows(SyntaxError, () => JSON.rawJSON('\uFEFF1'));
    assertThrows(SyntaxError, () => JSON.rawJSON('[]'));
    assertThrows(SyntaxError, () => JSON.rawJSON('{}'));
    // Valid primitives, and a number of any length
    assertEquals('-1.5e+300', JSON.stringify(JSON.rawJSON('-1.5e+300')));
    assertEquals('"\\u0041"', JSON.stringify(JSON.rawJSON('"\\u0041"')));
    const long = '9'.repeat(2000);
    assertEquals(long, JSON.stringify(JSON.rawJSON(long)));
}
