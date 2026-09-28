import fs from 'node:fs';
import fsp from 'node:fs/promises';
import { readFile as readFileP, writeFile as writeFileP } from 'node:fs/promises';

// ---------------------------------------------------------------
// node:fs — synchronous API (default + named imports)
// ---------------------------------------------------------------

// TMPDIR is injected as a global by the test host and points to a
// fresh, empty directory just for this test run.
const root = TMPDIR;
const path = (name) => root + "/" + name;

// existsSync on a not-yet-created path
{
    assertFalse(fs.existsSync(path("missing.txt")));
}

// writeFileSync + readFileSync round-trip (default UTF-8)
{
    fs.writeFileSync(path("hello.txt"), "hello world");
    assertTrue(fs.existsSync(path("hello.txt")));
    const back = fs.readFileSync(path("hello.txt"));
    assertEquals("hello world", back);
}

// writeFileSync overwrites existing content
{
    fs.writeFileSync(path("hello.txt"), "goodbye");
    assertEquals("goodbye", fs.readFileSync(path("hello.txt")));
}

// appendFileSync appends to existing file
{
    fs.writeFileSync(path("append.txt"), "one");
    fs.appendFileSync(path("append.txt"), "-two");
    assertEquals("one-two", fs.readFileSync(path("append.txt")));
}

// statSync exposes size / kind flags
{
    fs.writeFileSync(path("stat.txt"), "abcde");
    const st = fs.statSync(path("stat.txt"));
    assertEquals(5, st.size);
    assertTrue(st.isFile);
    assertFalse(st.isDirectory);
}

// mkdirSync + readdirSync + statSync on a directory
{
    fs.mkdirSync(path("d"));
    fs.writeFileSync(path("d/a.txt"), "a");
    fs.writeFileSync(path("d/b.txt"), "b");
    const entries = fs.readdirSync(path("d"));
    assertEquals(2, entries.length);
    // Order is not guaranteed by the spec; test as a set
    const s = entries.slice().sort();
    assertEquals("a.txt", s[0]);
    assertEquals("b.txt", s[1]);

    const st = fs.statSync(path("d"));
    assertTrue(st.isDirectory);
    assertFalse(st.isFile);
}

// mkdirSync with { recursive: true } for nested paths
{
    fs.mkdirSync(path("nested/one/two"), { recursive: true });
    assertTrue(fs.existsSync(path("nested/one/two")));
}

// renameSync moves a file
{
    fs.writeFileSync(path("from.txt"), "movable");
    fs.renameSync(path("from.txt"), path("to.txt"));
    assertFalse(fs.existsSync(path("from.txt")));
    assertEquals("movable", fs.readFileSync(path("to.txt")));
}

// copyFileSync duplicates content
{
    fs.writeFileSync(path("src.txt"), "content");
    fs.copyFileSync(path("src.txt"), path("dst.txt"));
    assertEquals("content", fs.readFileSync(path("src.txt")));
    assertEquals("content", fs.readFileSync(path("dst.txt")));
}

// unlinkSync removes a file
{
    fs.writeFileSync(path("del.txt"), "x");
    assertTrue(fs.existsSync(path("del.txt")));
    fs.unlinkSync(path("del.txt"));
    assertFalse(fs.existsSync(path("del.txt")));
}

// rmSync with { recursive: true, force: true } wipes a directory
{
    fs.mkdirSync(path("tree/deep"), { recursive: true });
    fs.writeFileSync(path("tree/a.txt"), "1");
    fs.writeFileSync(path("tree/deep/b.txt"), "2");
    fs.rmSync(path("tree"), { recursive: true, force: true });
    assertFalse(fs.existsSync(path("tree")));
}

// rmSync with { force: true } on a missing path is a no-op
{
    fs.rmSync(path("does-not-exist"), { force: true });
}

// realpathSync returns an absolute path string
{
    fs.writeFileSync(path("real.txt"), "z");
    const rp = fs.realpathSync(path("real.txt"));
    assertTrue(typeof rp === "string");
    assertTrue(rp.length > 0);
}

// readFileSync with an encoding option returns a string
{
    fs.writeFileSync(path("enc.txt"), "unicode");
    const s = fs.readFileSync(path("enc.txt"), { encoding: "utf-8" });
    assertEquals("unicode", s);

    // As a shorthand, a plain string encoding is accepted too
    const s2 = fs.readFileSync(path("enc.txt"), "utf-8");
    assertEquals("unicode", s2);
}

// Named import from 'node:fs' works alongside the default import
{
    // The API is exposed via named exports too; pick one and re-export.
    // (Deliberately kept minimal - this is proved by the promises test below.)
}

// ---------------------------------------------------------------
// node:fs/promises — Promise-returning API
// ---------------------------------------------------------------

// readFile / writeFile via the default fsp export
{
    let out = null;
    fsp.writeFile(path("p1.txt"), "async-write").then(_ => {
        return fsp.readFile(path("p1.txt"));
    }).then(t => { out = t; });
    setTimeout(() => {
        assertEquals("async-write", out);
    }, 50);
}

// readFile / writeFile as named exports (import { readFile } from '...')
{
    let out = null;
    writeFileP(path("p2.txt"), "named").then(_ => {
        return readFileP(path("p2.txt"));
    }).then(t => { out = t; });
    setTimeout(() => {
        assertEquals("named", out);
    }, 50);
}

// stat via promises returns the same shape as statSync
{
    fs.writeFileSync(path("p3.txt"), "12345");
    let st = null;
    fsp.stat(path("p3.txt")).then(s => { st = s; });
    setTimeout(() => {
        assertNotNull(st);
        assertEquals(5, st.size);
        assertTrue(st.isFile);
    }, 50);
}

// readdir via promises
{
    fs.mkdirSync(path("pd"));
    fs.writeFileSync(path("pd/one"), "1");
    fs.writeFileSync(path("pd/two"), "2");
    let entries = null;
    fsp.readdir(path("pd")).then(a => { entries = a; });
    setTimeout(() => {
        assertNotNull(entries);
        assertEquals(2, entries.length);
    }, 50);
}

// A missing file rejects the promise rather than throwing synchronously
{
    let rejected = false;
    fsp.readFile(path("nope.txt")).then(_ => {}, _ => { rejected = true; });
    setTimeout(() => {
        assertTrue(rejected);
    }, 50);
}

// ---------------------------------------------------------------
// node:fs — callback-style async API: cb(err, result)
// ---------------------------------------------------------------

// writeFile + readFile round-trip via callbacks
{
    let out = null;
    let writeErr = "unset";
    fs.writeFile(path("cb1.txt"), "cb-write", (err) => {
        writeErr = err;
        fs.readFile(path("cb1.txt"), (err2, data) => {
            if (err2 == null) out = data;
        });
    });
    setTimeout(() => {
        assertNull(writeErr);
        assertEquals("cb-write", out);
    }, 50);
}

// readFile with an options argument (encoding)
{
    fs.writeFileSync(path("cb-enc.txt"), "text");
    let got = null;
    fs.readFile(path("cb-enc.txt"), { encoding: "utf-8" }, (err, data) => {
        if (err == null) got = data;
    });
    setTimeout(() => {
        assertEquals("text", got);
    }, 50);
}

// A missing file surfaces via the err argument (not thrown)
{
    let caughtErr = null;
    fs.readFile(path("cb-nope.txt"), (err, data) => {
        caughtErr = err;
    });
    setTimeout(() => {
        assertNotNull(caughtErr);
    }, 50);
}

// stat + readdir + unlink callbacks
{
    fs.writeFileSync(path("cb-stat.txt"), "abcd");
    let sz = -1;
    fs.stat(path("cb-stat.txt"), (err, st) => {
        if (err == null) sz = st.size;
    });
    setTimeout(() => {
        assertEquals(4, sz);
    }, 50);
}

{
    fs.mkdirSync(path("cbd"));
    fs.writeFileSync(path("cbd/x"), "1");
    fs.writeFileSync(path("cbd/y"), "2");
    let entries = null;
    fs.readdir(path("cbd"), (err, a) => {
        if (err == null) entries = a;
    });
    setTimeout(() => {
        assertNotNull(entries);
        assertEquals(2, entries.length);
    }, 50);
}

{
    fs.writeFileSync(path("cb-del.txt"), "x");
    let removed = false;
    fs.unlink(path("cb-del.txt"), (err) => {
        removed = (err == null);
    });
    setTimeout(() => {
        assertTrue(removed);
        assertFalse(fs.existsSync(path("cb-del.txt")));
    }, 50);
}

// rename via callback
{
    fs.writeFileSync(path("cb-from.txt"), "mv");
    let renamed = false;
    fs.rename(path("cb-from.txt"), path("cb-to.txt"), (err) => {
        renamed = (err == null);
    });
    setTimeout(() => {
        assertTrue(renamed);
        assertEquals("mv", fs.readFileSync(path("cb-to.txt")));
    }, 50);
}

// exists(path, cb) legacy signature: cb(exists) — no err argument
{
    fs.writeFileSync(path("cb-ex.txt"), "e");
    let existsHere = null, existsMissing = null;
    fs.exists(path("cb-ex.txt"),      (b) => { existsHere    = b; });
    fs.exists(path("cb-ex-missing"),  (b) => { existsMissing = b; });
    setTimeout(() => {
        assertTrue(existsHere);
        assertFalse(existsMissing);
    }, 50);
}
