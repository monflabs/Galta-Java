// ---------------------------------------------------------------
// setTimeout, clearTimeout, setInterval, clearInterval
// ---------------------------------------------------------------

// setTimeout returns a numeric id
{
    let fired = false;
    const id = setTimeout(() => { fired = true; }, 10);
    assertTrue(typeof id === "number");
    // Callback hasn't fired yet at this point (it's a timer macrotask).
    assertFalse(fired);
}

// setTimeout callback fires with the extra arguments
{
    let received = null;
    setTimeout((a, b, c) => { received = [a, b, c]; }, 10, "x", 42, true);
    // Verify from another timeout that runs later.
    setTimeout(() => {
        assertNotNull(received);
        assertEquals("x", received[0]);
        assertEquals(42, received[1]);
        assertEquals(true, received[2]);
    }, 30);
}

// setTimeout with 0/negative delay still runs
{
    let a = 0, b = 0;
    setTimeout(() => { a = 1; }, 0);
    setTimeout(() => { b = 1; }, -100);
    setTimeout(() => {
        assertEquals(1, a);
        assertEquals(1, b);
    }, 30);
}

// clearTimeout prevents the callback from running
{
    let ran = false;
    const id = setTimeout(() => { ran = true; }, 20);
    clearTimeout(id);
    setTimeout(() => {
        assertFalse(ran);
    }, 50);
}

// clearTimeout with an unknown id is a no-op (must not throw)
clearTimeout(999999);
clearTimeout(undefined);
clearTimeout(null);

// setTimeout with a non-callable first arg returns 0 and does nothing
{
    const id = setTimeout("not a function", 10);
    assertEquals(0, id);
}

// setInterval fires repeatedly and clearInterval stops it
{
    let count = 0;
    const id = setInterval(() => {
        count++;
        if (count >= 3) {
            clearInterval(id);
        }
    }, 10);
    setTimeout(() => {
        // Well after 30ms the interval must have fired exactly 3 times and
        // stopped (generous margin: timers drift on a cold JVM)
        assertEquals(3, count);
    }, 200);
}

// setInterval passes extra arguments through on every tick
{
    const seen = [];
    const id = setInterval((tag) => {
        seen.push(tag);
        if (seen.length >= 2) {
            clearInterval(id);
        }
    }, 10, "tick");
    setTimeout(() => {
        assertEquals(2, seen.length);
        assertEquals("tick", seen[0]);
        assertEquals("tick", seen[1]);
    }, 80);
}

// clearInterval before the first tick prevents any firing
{
    let fired = false;
    const id = setInterval(() => { fired = true; }, 10);
    clearInterval(id);
    setTimeout(() => {
        assertFalse(fired);
    }, 50);
}

// clearTimeout and clearInterval are interchangeable (per HTML spec)
{
    let a = false, b = false;
    const t = setTimeout(() => { a = true; }, 20);
    const i = setInterval(() => { b = true; }, 20);
    // Cross-cancel: use clearInterval on a setTimeout id, and vice versa.
    clearInterval(t);
    clearTimeout(i);
    setTimeout(() => {
        assertFalse(a);
        assertFalse(b);
    }, 60);
}

// Ordering: earlier-scheduled shorter delays fire before later, longer ones
{
    const order = [];
    setTimeout(() => order.push("c"), 40);
    setTimeout(() => order.push("a"), 10);
    setTimeout(() => order.push("b"), 20);
    setTimeout(() => {
        assertEquals(3, order.length);
        assertEquals("a", order[0]);
        assertEquals("b", order[1]);
        assertEquals("c", order[2]);
    }, 80);
}

// ---------------------------------------------------------------
// queueMicrotask
// ---------------------------------------------------------------

// queueMicrotask runs the callback asynchronously (not synchronously)
{
    let ran = false;
    queueMicrotask(() => { ran = true; });
    assertFalse(ran);
    setTimeout(() => {
        assertTrue(ran);
    }, 20);
}

// Multiple queued microtasks run in FIFO order
{
    const order = [];
    queueMicrotask(() => order.push("a"));
    queueMicrotask(() => order.push("b"));
    queueMicrotask(() => order.push("c"));
    setTimeout(() => {
        assertEquals(3, order.length);
        assertEquals("a", order[0]);
        assertEquals("b", order[1]);
        assertEquals("c", order[2]);
    }, 20);
}

// queueMicrotask with a non-callable arg throws
{
    let threw = false;
    try {
        queueMicrotask("not a function");
    } catch (e) {
        threw = true;
    }
    assertTrue(threw);
}

// ---------------------------------------------------------------
// btoa / atob
// ---------------------------------------------------------------

// btoa: basic ASCII encoding
assertEquals("SGVsbG8sIFdvcmxkIQ==", btoa("Hello, World!"));
assertEquals("", btoa(""));
assertEquals("Zg==", btoa("f"));
assertEquals("Zm8=", btoa("fo"));
assertEquals("Zm9v", btoa("foo"));
assertEquals("Zm9vYg==", btoa("foob"));
assertEquals("Zm9vYmE=", btoa("fooba"));
assertEquals("Zm9vYmFy", btoa("foobar"));

// atob: basic decoding, round-trips btoa
assertEquals("Hello, World!", atob("SGVsbG8sIFdvcmxkIQ=="));
assertEquals("", atob(""));
assertEquals("f", atob("Zg=="));
assertEquals("fo", atob("Zm8="));
assertEquals("foo", atob("Zm9v"));
assertEquals("foobar", atob("Zm9vYmFy"));

// atob tolerates whitespace in input
assertEquals("foobar", atob("Zm9v YmFy"));
assertEquals("foobar", atob("Zm9v\nYmFy"));
assertEquals("foobar", atob("Zm9v\tYmFy"));

// atob tolerates missing "=" padding (forgiving-base64)
assertEquals("f", atob("Zg"));
assertEquals("fo", atob("Zm8"));
assertEquals("foob", atob("Zm9vYg"));

// btoa throws on chars outside Latin1
{
    let threw = false;
    try {
        btoa("\u{1F600}"); // emoji — code point > 0xFF
    } catch (e) {
        threw = true;
    }
    assertTrue(threw);
}

// atob throws on invalid Base64 length (mod 4 == 1 is unrecoverable)
{
    let threw = false;
    try {
        atob("Z");
    } catch (e) {
        threw = true;
    }
    assertTrue(threw);
}

// btoa coerces non-string input via ToString
assertEquals("MTIz", btoa(123));
assertEquals("dHJ1ZQ==", btoa(true));

// A timer callback is a macrotask: it runs after the pending promise
// reactions (microtasks), even with a 0 delay
{
    const order = [];
    setTimeout(() => order.push("timeout"));
    Promise.resolve().then(() => order.push("promise1")).then(() => order.push("promise2"));
    queueMicrotask(() => order.push("microtask"));
    setTimeout(() => {
        assertEquals("promise1,microtask,promise2,timeout", order.join());
    }, 20);
}

// Due timers run in order, each followed by its own microtasks
{
    const order = [];
    setTimeout(() => { order.push("t1"); Promise.resolve().then(() => order.push("p1")); });
    setTimeout(() => order.push("t2"));
    setTimeout(() => {
        assertEquals("t1,p1,t2", order.join());
    }, 20);
}
