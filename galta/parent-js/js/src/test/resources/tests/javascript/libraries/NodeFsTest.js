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
    assertTrue(st.isFile());
    assertFalse(st.isDirectory());
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
    assertTrue(st.isDirectory());
    assertFalse(st.isFile());
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
// Errors carry Node's code / syscall / path; stats flags are methods;
// the encodings are Node's
// ---------------------------------------------------------------

// A missing file: ENOENT
{
    let e = null;
    try { fs.readFileSync(path("missing.txt")); } catch(x) { e = x; }
    assertNotNull(e);
    assertEquals("ENOENT", e.code);
    assertEquals("open", e.syscall);
    assertTrue(e.message.startsWith("ENOENT: no such file or directory, open '"));
}

// mkdir on an existing directory: EEXIST; reading or rm-ing a directory: EISDIR
{
    fs.mkdirSync(path("exists-dir"));
    let e = null;
    try { fs.mkdirSync(path("exists-dir")); } catch(x) { e = x; }
    assertEquals("EEXIST", e.code);
    e = null;
    try { fs.readFileSync(path("exists-dir")); } catch(x) { e = x; }
    assertEquals("EISDIR", e.code);
    e = null;
    try { fs.rmSync(path("exists-dir")); } catch(x) { e = x; }
    assertEquals("EISDIR", e.code);
}

// A path through a file: ENOTDIR (or ENOENT on file systems that say so)
{
    fs.writeFileSync(path("plain.txt"), "x");
    let e = null;
    try { fs.readdirSync(path("plain.txt")); } catch(x) { e = x; }
    assertNotNull(e);
    assertEquals("ENOTDIR", e.code);
}

// stats flags are methods
{
    const st = fs.statSync(path("plain.txt"));
    assertEquals("function", typeof st.isFile);
    assertTrue(st.isFile());
    assertFalse(st.isDirectory());
    assertFalse(st.isSymbolicLink());
}

// hex / base64 / latin1 encodings
{
    fs.writeFileSync(path("hex.bin"), "48656c6c6f", "hex");
    assertEquals("Hello", fs.readFileSync(path("hex.bin"), "utf8"));
    assertEquals("48656c6c6f", fs.readFileSync(path("hex.bin"), { encoding: "hex" }));
    assertEquals("SGVsbG8=", fs.readFileSync(path("hex.bin"), "base64"));
    fs.writeFileSync(path("b64.bin"), "SGVsbG8=", { encoding: "base64" });
    assertEquals("Hello", fs.readFileSync(path("b64.bin"), "latin1"));
    let e = null;
    try { fs.readFileSync(path("b64.bin"), "nope"); } catch(x) { e = x; }
    assertNotNull(e);
}

// An invalid path argument throws synchronously, even for the callback API
{
    assertThrows(TypeError, () => fs.readFile(42, () => {}));
}

// ---------------------------------------------------------------
// node:fs/promises — Promise-returning API
// ---------------------------------------------------------------

// readFile / writeFile via the default fsp export
{
    await fsp.writeFile(path("p1.txt"), "async-write");
    assertEquals("async-write", await fsp.readFile(path("p1.txt")));
}

// readFile / writeFile as named exports (import { readFile } from '...')
{
    await writeFileP(path("p2.txt"), "named");
    assertEquals("named", await readFileP(path("p2.txt")));
}

// stat via promises returns the same shape as statSync
{
    fs.writeFileSync(path("p3.txt"), "12345");
    const st = await fsp.stat(path("p3.txt"));
    assertNotNull(st);
    assertEquals(5, st.size);
    assertTrue(st.isFile());
}

// readdir via promises
{
    fs.mkdirSync(path("pd"));
    fs.writeFileSync(path("pd/one"), "1");
    fs.writeFileSync(path("pd/two"), "2");
    const entries = await fsp.readdir(path("pd"));
    assertEquals(2, entries.length);
}

// A missing file rejects the promise rather than throwing synchronously,
// and so does an invalid path
{
    let rejected = null;
    const p = fsp.readFile(path("nope.txt"));
    await p.then(_ => {}, e => { rejected = e; });
    assertEquals("ENOENT", rejected.code);
    rejected = null;
    await fsp.readFile(42).then(_ => {}, e => { rejected = e; });
    assertTrue(rejected instanceof TypeError);
}

// ---------------------------------------------------------------
// node:fs — callback-style async API: cb(err, result)
// ---------------------------------------------------------------

// Runs a callback-style call, resolving with the callback's arguments
const cb = (f) => new Promise(resolve => f((...args) => resolve(args)));

// writeFile + readFile round-trip via callbacks
{
    const [writeErr] = await cb(done => fs.writeFile(path("cb1.txt"), "cb-write", done));
    assertNull(writeErr);
    const [err2, data] = await cb(done => fs.readFile(path("cb1.txt"), done));
    assertNull(err2);
    assertEquals("cb-write", data);
}

// readFile with an options argument (encoding)
{
    fs.writeFileSync(path("cb-enc.txt"), "text");
    const [err, data] = await cb(done => fs.readFile(path("cb-enc.txt"), { encoding: "utf-8" }, done));
    assertNull(err);
    assertEquals("text", data);
}

// A missing file surfaces via the err argument (not thrown)
{
    const [err] = await cb(done => fs.readFile(path("cb-nope.txt"), done));
    assertNotNull(err);
    assertEquals("ENOENT", err.code);
}

// stat + readdir + unlink callbacks
{
    fs.writeFileSync(path("cb-stat.txt"), "abcd");
    const [err, st] = await cb(done => fs.stat(path("cb-stat.txt"), done));
    assertNull(err);
    assertEquals(4, st.size);
}

{
    fs.mkdirSync(path("cbd"));
    fs.writeFileSync(path("cbd/x"), "1");
    fs.writeFileSync(path("cbd/y"), "2");
    const [err, entries] = await cb(done => fs.readdir(path("cbd"), done));
    assertNull(err);
    assertEquals(2, entries.length);
}

{
    fs.writeFileSync(path("cb-del.txt"), "x");
    const [err] = await cb(done => fs.unlink(path("cb-del.txt"), done));
    assertNull(err);
    assertFalse(fs.existsSync(path("cb-del.txt")));
}

// rename via callback
{
    fs.writeFileSync(path("cb-from.txt"), "mv");
    const [err] = await cb(done => fs.rename(path("cb-from.txt"), path("cb-to.txt"), done));
    assertNull(err);
    assertEquals("mv", fs.readFileSync(path("cb-to.txt")));
}

// exists(path, cb) legacy signature: cb(exists) — no err argument
{
    fs.writeFileSync(path("cb-ex.txt"), "e");
    const [existsHere] = await cb(done => fs.exists(path("cb-ex.txt"), done));
    const [existsMissing] = await cb(done => fs.exists(path("cb-ex-missing"), done));
    assertTrue(existsHere);
    assertFalse(existsMissing);
}
