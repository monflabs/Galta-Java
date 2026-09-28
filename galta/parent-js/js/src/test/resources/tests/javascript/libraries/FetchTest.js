// ---------------------------------------------------------------
// Headers
// ---------------------------------------------------------------

// Empty Headers construction
{
    const h = new Headers();
    assertFalse(h.has("content-type"));
    assertEquals(null, h.get("content-type"));
}

// Headers from a plain object
{
    const h = new Headers({ "Content-Type": "text/plain", "X-Foo": "bar" });
    // Names are case-insensitive
    assertTrue(h.has("content-type"));
    assertTrue(h.has("CONTENT-TYPE"));
    assertEquals("text/plain", h.get("Content-Type"));
    assertEquals("bar", h.get("x-foo"));
}

// Headers from an array of [name, value] pairs
{
    const h = new Headers([["A", "1"], ["B", "2"]]);
    assertEquals("1", h.get("a"));
    assertEquals("2", h.get("b"));
}

// Headers copy-construction preserves entries
{
    const src = new Headers({ "X-Copy": "yes" });
    const dst = new Headers(src);
    assertEquals("yes", dst.get("x-copy"));
}

// append() combines repeated values with ", "
{
    const h = new Headers();
    h.append("Accept", "text/html");
    h.append("Accept", "application/json");
    assertEquals("text/html, application/json", h.get("accept"));
}

// set() replaces all previous values
{
    const h = new Headers();
    h.append("X", "1");
    h.append("X", "2");
    h.set("X", "3");
    assertEquals("3", h.get("x"));
}

// delete() removes the entry
{
    const h = new Headers({ "X-Del": "gone" });
    assertTrue(h.has("x-del"));
    h.delete("X-Del");
    assertFalse(h.has("x-del"));
    assertEquals(null, h.get("x-del"));
}

// values(), keys(), entries()
{
    const h = new Headers();
    h.append("A", "1");
    h.append("B", "2");
    const keys = h.keys();
    assertEquals(2, keys.length);
    // Keys are stored lowercase per spec.
    assertEquals("a", keys[0]);
    assertEquals("b", keys[1]);

    const vals = h.values();
    assertEquals(2, vals.length);
    assertEquals("1", vals[0]);
    assertEquals("2", vals[1]);

    const entries = h.entries();
    assertEquals(2, entries.length);
    assertEquals("a", entries[0][0]);
    assertEquals("1", entries[0][1]);
    assertEquals("b", entries[1][0]);
    assertEquals("2", entries[1][1]);
}

// forEach() iterates value, name, headers
{
    const h = new Headers({ "K1": "v1", "K2": "v2" });
    const seen = [];
    h.forEach((value, name, headersRef) => {
        seen.push([name, value]);
        assertTrue(headersRef === h);
    });
    assertEquals(2, seen.length);
}

// Values are trimmed on insert
{
    const h = new Headers();
    h.append("A", "  padded  ");
    assertEquals("padded", h.get("a"));
}

// ---------------------------------------------------------------
// Request
// ---------------------------------------------------------------

// Default Request: GET, no body, empty headers
{
    const r = new Request("http://example.test/");
    assertEquals("http://example.test/", r.url);
    assertEquals("GET", r.method);
    assertFalse(r.bodyUsed);
    assertNotNull(r.headers);
}

// Method is uppercased
{
    const r = new Request("http://example.test/", { method: "post" });
    assertEquals("POST", r.method);
}

// Init object supplies headers and body
{
    const r = new Request("http://example.test/", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: "{\"k\":1}"
    });
    assertEquals("POST", r.method);
    assertEquals("application/json", r.headers.get("content-type"));
}

// Copy-construct a Request from another Request
{
    const r1 = new Request("http://example.test/a", {
        method: "PUT",
        headers: { "X-Tag": "one" },
        body: "hello"
    });
    const r2 = new Request(r1);
    assertEquals("http://example.test/a", r2.url);
    assertEquals("PUT", r2.method);
    assertEquals("one", r2.headers.get("x-tag"));

    // Modifying r2's headers must NOT mutate r1's headers (copy semantics)
    r2.headers.set("X-Tag", "two");
    assertEquals("one", r1.headers.get("x-tag"));
    assertEquals("two", r2.headers.get("x-tag"));
}

// clone() produces an independent Request
{
    const r1 = new Request("http://example.test/", { method: "POST", body: "abc" });
    const r2 = r1.clone();
    assertEquals(r1.url, r2.url);
    assertEquals(r1.method, r2.method);
    r2.headers.append("X-More", "yes");
    assertFalse(r1.headers.has("x-more"));
}

// ---------------------------------------------------------------
// Response
// ---------------------------------------------------------------

// Default Response construction
{
    const r = new Response();
    assertEquals(200, r.status);
    assertTrue(r.ok);
    assertFalse(r.bodyUsed);
    assertEquals("", r.url);
}

// Response with body and init
{
    const r = new Response("hello", { status: 201, statusText: "Created", headers: { "X-K": "v" } });
    assertEquals(201, r.status);
    assertEquals("Created", r.statusText);
    assertTrue(r.ok);
    assertEquals("v", r.headers.get("x-k"));
}

// Non-2xx status flips .ok to false
{
    const r = new Response("nope", { status: 404 });
    assertEquals(404, r.status);
    assertFalse(r.ok);
}

// Response.text() returns a Promise resolving to the body string
{
    const r = new Response("hello world");
    // bodyUsed flips synchronously when text() is called, before the
    // Promise resolves.
    assertFalse(r.bodyUsed);
    let text = null;
    const p = r.text();
    assertTrue(r.bodyUsed);
    p.then(t => { text = t; });
    setTimeout(() => {
        assertEquals("hello world", text);
    }, 30);
}

// Response.json() parses the body as JSON
{
    const r = new Response("{\"a\":1,\"b\":\"two\"}");
    let parsed = null;
    r.json().then(v => { parsed = v; });
    setTimeout(() => {
        assertNotNull(parsed);
        assertEquals(1, parsed.a);
        assertEquals("two", parsed.b);
    }, 30);
}

// Response.json() on invalid JSON rejects
{
    const r = new Response("not json");
    let rejected = false;
    r.json().then(_ => {}, _ => { rejected = true; });
    setTimeout(() => {
        assertTrue(rejected);
    }, 30);
}

// Body can only be consumed once
{
    const r = new Response("once");
    let firstOk = false;
    let secondRejected = false;
    r.text().then(t => { firstOk = (t === "once"); });
    setTimeout(() => {
        // Second read after first has completed rejects
        r.text().then(_ => {}, _ => { secondRejected = true; });
    }, 20);
    setTimeout(() => {
        assertTrue(firstOk);
        assertTrue(secondRejected);
    }, 60);
}

// clone() creates an independent Response whose body is still readable
{
    const r1 = new Response("shared");
    const r2 = r1.clone();
    let a = null, b = null;
    r1.text().then(t => { a = t; });
    r2.text().then(t => { b = t; });
    setTimeout(() => {
        assertEquals("shared", a);
        assertEquals("shared", b);
    }, 30);
}
