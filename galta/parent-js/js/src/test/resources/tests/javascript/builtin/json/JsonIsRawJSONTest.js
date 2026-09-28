// Test file for JSON.isRawJSON

function test_JSON_isRawJSON() {
    // Basic positive test
    {
        const raw = JSON.parse('123', (_, value) => JSON.rawJSON(value));
        assertTrue(JSON.isRawJSON(raw));
    }

    // Test: rawJSON inside an object
    {
        const obj = JSON.parse('{"foo":123}', (key, value) => {
            if (key === 'foo') return JSON.rawJSON(value);
            return value;
        });
        assertTrue(JSON.isRawJSON(obj.foo));
        assertFalse(JSON.isRawJSON(obj)); // The object itself is not raw
    }

    // Test: rawJSON inside an array
    {
		// The root value is the array holding the raw JSON object made for 123:
		// ToString() of that array reaches the raw JSON object, which has no
		// toString (null prototype) - a TypeError, as in V8
		assertThrows( TypeError, () => JSON.parse('[123]', (_, value) => JSON.rawJSON(value)));
    }

    // Negative tests: simple values are not raw
    {
        assertFalse(JSON.isRawJSON(null));
        assertFalse(JSON.isRawJSON(undefined));
        assertFalse(JSON.isRawJSON(0));
        assertFalse(JSON.isRawJSON(1));
        assertFalse(JSON.isRawJSON(NaN));
        assertFalse(JSON.isRawJSON(Infinity));
        assertFalse(JSON.isRawJSON(true));
        assertFalse(JSON.isRawJSON(false));
        assertFalse(JSON.isRawJSON(''));
        assertFalse(JSON.isRawJSON('abc'));
    }

    // Negative tests: objects and arrays normally are not raw
    {
        assertFalse(JSON.isRawJSON({}));
        assertFalse(JSON.isRawJSON({ foo: 123 }));
        assertFalse(JSON.isRawJSON([]));
        assertFalse(JSON.isRawJSON([1, 2, 3]));
    }

    // Negative tests: functions are not raw
    {
        function f() {}
        assertFalse(JSON.isRawJSON(f));
        assertFalse(JSON.isRawJSON(() => {}));
    }

    // Negative tests: dates and regex are not raw
    {
        assertFalse(JSON.isRawJSON(new Date()));
        assertFalse(JSON.isRawJSON(/abc/));
    }

    // Test: when manually constructing a fake "raw" looking object
    {
        const fakeRaw = { __raw_json__: true };
        assertFalse(JSON.isRawJSON(fakeRaw)); // Should not be tricked by similar shape
    }

    // Test: error cases (should not throw)
    {
        try {
            JSON.isRawJSON(); // no argument
            JSON.isRawJSON(Symbol('test'));
            JSON.isRawJSON(BigInt(10));
            JSON.isRawJSON(Object.create(null)); // plain dictionary object
            JSON.isRawJSON(new Map());
            JSON.isRawJSON(new Set());
        } catch (e) {
            fail(); // JSON.isRawJSON should never throw, only return true or false
        }
    }

    // Type check: must return boolean
    {
        assertSame(true, JSON.isRawJSON(JSON.rawJSON(42)));
        assertSame(false, JSON.isRawJSON(42));
    }
}

test_JSON_isRawJSON();
