// ---------------------------------------------------------------
// version()
// ---------------------------------------------------------------

// version() returns the current version string (default is "200")
{
    const v = version();
    assertTrue(typeof v === "string");
    assertEquals("200", v);
}

// version(x) sets the version and returns the previous or new value
{
    version("185");
    assertEquals("185", version());
    // Restore for later tests
    version("200");
    assertEquals("200", version());
}

// version() with an empty string is ignored and does not change the value
{
    version("200");
    version("");
    assertEquals("200", version());
}

// ---------------------------------------------------------------
// print()
// ---------------------------------------------------------------

// Discard whatever startup output may have accumulated
captureOutput();

// print() writes its argument followed by a newline
{
    print("hello");
    const out = captureOutput();
    // Some platforms use \r\n; check the payload with startsWith
    assertTrue(out === "hello\n" || out === "hello\r\n");
}

// print() concatenates multiple arguments without separators, then newline
{
    print("a", "b", "c");
    const out = captureOutput();
    assertTrue(out === "abc\n" || out === "abc\r\n");
}

// print() coerces non-string arguments via ToString
{
    print(1, 2, 3);
    const out = captureOutput();
    assertTrue(out === "123\n" || out === "123\r\n");
}

// print() with no arguments still emits a newline
{
    print();
    const out = captureOutput();
    assertTrue(out === "\n" || out === "\r\n");
}

// ---------------------------------------------------------------
// options()
// ---------------------------------------------------------------

// options() with no args returns a status string
{
    const s = options();
    assertTrue(typeof s === "string");
    assertTrue(s.length > 0);
}

// options(name) is accepted and returns undefined
{
    const r = options("strict");
    assertTrue(r === undefined);
}

// ---------------------------------------------------------------
// gc()
// ---------------------------------------------------------------

// gc() returns undefined and does not throw
{
    const r = gc();
    assertTrue(r === undefined);
}
