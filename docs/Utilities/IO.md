# I/O & Paths

`org.monflabs.util` has three static helper classes for everyday I/O: `IOStreamUtil` (streams, readers, writers), `FileUtil` (`java.io.File`) and `PathUtil` (path strings, with a configurable separator). The `org.monflabs.util.io` package adds a few stream classes: bridges between byte and character streams, unsynchronized buffers, a rolling character buffer and null streams.

For virtual file systems (in-memory, zip, sandboxed directories) see [File Systems](/Utilities/FileSystems).

## Character sets

Methods that take a `Charset` use it. Their overloads without one use UTF-8, except `ReaderInputStream`, which uses the JVM default charset (UTF-8 from Java 18 on).

| Without a charset | Explicit variant |
|---|---|
| `IOStreamUtil.readContent(InputStream)` (UTF-8) | `readContent(InputStream, Charset)` |
| `IOStreamUtil.setContent(OutputStream, String)` (UTF-8) | `setContent(OutputStream, String, Charset)` |
| `FileUtil.readContent(File)` (UTF-8) | `readContent(File, Charset)` |
| `FileUtil.setContent(File, String)` (UTF-8) | `setContent(File, String, Charset)` |
| `new ReaderInputStream(reader)` (`Charset.defaultCharset()`) | `new ReaderInputStream(reader, Charset)` or `(reader, "encodingName")` |
| `new WriterOutputStream(writer)` (UTF-8) | `new WriterOutputStream(writer, Charset)` or `(writer, CharsetDecoder)` |
| `LRUCachedOutputStream` (decodes the bytes it caches, UTF-8) | none |

## IOStreamUtil

| Method | Does |
|---|---|
| `readContent(InputStream[, Charset])`, `readContent(Reader)` | reads everything into a `String` |
| `readBytes(InputStream)` | reads everything into a `byte[]` |
| `setContent(OutputStream, String[, Charset])` | writes the string and flushes |
| `setContent(Writer, String)` | writes the string |
| `close(AutoCloseable)` | closes; ignores `null`; wraps an exception in a `ForwardRuntimeException` |

None of them closes the stream it is given.

Sample: `doc_examples/util/IOExamples.java` (`testIOStreamUtil`)

```java
InputStream in = new ByteArrayInputStream("h\u00e9llo".getBytes(StandardCharsets.UTF_8));
assertEquals("h\u00e9llo", IOStreamUtil.readContent(in, StandardCharsets.UTF_8));

ByteArrayOutputStream out = new ByteArrayOutputStream();
IOStreamUtil.setContent(out, "h\u00e9llo", StandardCharsets.UTF_8);   // flushed, not closed
assertEquals(6, out.size());
assertArrayEquals(out.toByteArray(), IOStreamUtil.readBytes(new ByteArrayInputStream(out.toByteArray())));

IOStreamUtil.close(null);   // null is ignored
ForwardRuntimeException e = assertThrows(ForwardRuntimeException.class,
        () -> IOStreamUtil.close(() -> { throw new java.io.IOException("disk gone"); }));
assertEquals("Error while closing stream", e.getMessage());
```

## FileUtil

| Method | Does |
|---|---|
| `readContent(File[, Charset])` | reads a text file |
| `setContent(File, String[, Charset])` | writes a text file, replacing it |
| `copy(File src, File tgt)` | copies the bytes |
| `prepareDirectory(dir[, clear])` | creates the directory and its parents; by default first deletes it if it exists |
| `emptyDirectory(dir)` | deletes the content, keeps the directory; `false` if it is not a directory or something could not be deleted |
| `deleteFile(file)` | deletes a file or a directory tree; `false` if something could not be deleted |

`FileUtil` throws unchecked exceptions: I/O errors come back as a `ForwardRuntimeException` naming the file.

Sample: `doc_examples/util/IOExamples.java` (`testFileUtil`)

```java
File dir = new File(Files.createTempDirectory("doc").toFile(), "out");
FileUtil.prepareDirectory(dir);                  // creates it (and empties it if it existed)
File f = new File(dir, "notes.txt");
FileUtil.setContent(f, "caf\u00e9", StandardCharsets.UTF_8);
assertEquals("caf\u00e9", FileUtil.readContent(f, StandardCharsets.UTF_8));

File copy = new File(dir, "copy.txt");
FileUtil.copy(f, copy);
assertEquals(5, copy.length());

assertTrue(FileUtil.emptyDirectory(dir));       // deletes the content, keeps the directory
assertEquals(0, dir.list().length);
assertTrue(FileUtil.deleteFile(dir.getParentFile()));   // recursive

ForwardRuntimeException e = assertThrows(ForwardRuntimeException.class, () -> FileUtil.readContent(f));
assertEquals("Error while reading file " + f.getPath(), e.getMessage());
```

## PathUtil

`PathUtil` manipulates path strings; it never touches the file system. An instance is bound to a separator:

| Instance | Separator |
|---|---|
| `PathUtil.POSIX` | `/` |
| `PathUtil.WIN` | `\` |
| `PathUtil.FILE` | `File.separatorChar` |
| `PathUtil.FILE_AGNOSTIC` | `File.separatorChar`, and the other one accepted on input |
| `PathUtil.DOT` | `.`, for qualified names such as `java.util.List` |
| `PathUtil.of(sep[, sep2])` | your own |

### Names and extensions

`getParentPath` returns `""` for a name without a separator and `null` for `null` or `""`. The extension is whatever follows the last `.` of the file name; a dot in a directory name does not count.

Sample: `doc_examples/util/IOExamples.java` (`testPathUtilNames`)

```java
PathUtil p = PathUtil.POSIX;
assertEquals("/a/b", p.getParentPath("/a/b/c.tar.gz"));
assertEquals("", p.getParentPath("c.txt"));
assertEquals("c.tar.gz", p.getFileName("/a/b/c.tar.gz"));
assertEquals("gz", p.getFileExtension("/a/b/c.tar.gz"));
assertEquals("", p.getFileExtension("/a.d/Makefile"));      // the dot must be in the file name
assertEquals("/a/b/c.tar", p.removeExtension("/a/b/c.tar.gz"));
assertEquals("/a/b/c.tar.bz2", p.setExtension("/a/b/c.tar.gz", "bz2"));
```

### Composing and relative paths

| Method | Does |
|---|---|
| `concat(a, b)` | joins with one separator, removing at most one trailing separator from `a` and one leading separator from `b`; an empty side returns the other |
| `concat(a, b, c...)` | same for any number of parts, skipping `null` and empty ones |
| `getParts(path)` | splits on the separator(s) after removing one leading and one trailing separator |
| `compose(parts[, offset, limit])` | joins parts with the separator |
| `removeLeadingSep`, `removeTrailingSep`, `removeSep` | remove one separator |
| `getRelativePath(base, file)` | `file` relative to `base`, `""` if equal, `null` if `file` is not inside `base` |

`getRelativePath` works on whole segments: `/a/bc` is not inside `/a/b`.

Sample: `doc_examples/util/IOExamples.java` (`testPathUtilCompose`)

```java
PathUtil p = PathUtil.POSIX;
assertEquals("a/b", p.concat("a/", "/b"));
assertEquals("/a/b/c", p.concat("/a", "b/", "c"));        // varargs: empty/null parts skipped
assertArrayEquals(new String[] {"a", "b", "c"}, p.getParts("/a/b/c/"));
assertEquals("b/c", p.compose(new String[] {"a", "b", "c"}, 1, 2));

assertEquals("c/d", p.getRelativePath("/a/b", "/a/b/c/d"));
assertEquals("", p.getRelativePath("/a/b", "/a/b"));
assertNull(p.getRelativePath("/a/b", "/a/bc"));            // not below the base
assertEquals("a/b", p.getRelativePath("/", "/a/b"));
```

With a second separator, both are accepted on input; `normalize(path)` rewrites the second into the first, and composing methods write the first:

Sample: `doc_examples/util/IOExamples.java` (`testPathUtilSeparators`)

```java
assertEquals("C:\\dir", PathUtil.WIN.getParentPath("C:\\dir\\file.txt"));

// A second separator is accepted on input and normalized to the first one
PathUtil both = PathUtil.of('/', '\\');
assertEquals("file.txt", both.getFileName("dir\\sub/file.txt"));
assertEquals("dir/sub/file.txt", both.normalize("dir\\sub/file.txt"));

assertEquals("java.util", PathUtil.DOT.getParentPath("java.util.List"));
```

## Stream classes

| Class | Purpose |
|---|---|
| `ReaderInputStream` | an `InputStream` over a `Reader`, encoding the characters |
| `WriterOutputStream` | an `OutputStream` into a `Writer`, decoding the bytes (with a `Charset`, malformed input is replaced, not rejected); every `write` flushes the writer |
| `FastStringReader` | an unsynchronized `Reader` over a `String`, with an optional start index and `mark`/`reset` |
| `FastBufferedInputStream`, `FastBufferedOutputStream`, `FastBufferedReader`, `FastBufferedWriter` | unsynchronized buffered streams (8 KB for input, 16 KB for output by default); `get(stream)` (except on the reader) wraps a stream unless it already is one |
| `LRUCharBuffer.MemoryCharBuffer` | a bounded buffer that keeps the most recent characters |
| `LRUCachedOutputStream` | forwards bytes to an optional stream and keeps their decoded tail in an `LRUCharBuffer` |
| `NullInputStream`, `NullOutputStream`, `NullReader`, `NullWriter` | empty sources and discarding sinks, each with a shared `instance` |
| `StreamSuppliers` | `Supplier`s that open a file input or output stream |

Sample: `doc_examples/util/IOExamples.java` (`testReaderAndWriterBridges`, `testFastStringReader`)

```java
InputStream in = new ReaderInputStream(new StringReader("\u00e9t\u00e9"), StandardCharsets.UTF_8);
assertEquals(5, IOStreamUtil.readBytes(in).length);             // chars encoded to bytes

StringWriter w = new StringWriter();
WriterOutputStream os = new WriterOutputStream(w, StandardCharsets.UTF_8);
os.write("\u00e9t\u00e9".getBytes(StandardCharsets.UTF_8));    // bytes decoded to chars
os.close();
assertEquals("\u00e9t\u00e9", w.toString());

FastStringReader r = new FastStringReader("abcdef", 2);   // starts at index 2
assertEquals('c', r.read());
r.mark(0);
assertEquals(2, r.skip(2));
r.reset();
assertEquals('d', r.read());
```

### Keeping the tail of an output

`LRUCharBuffer.MemoryCharBuffer(capacity, offset)` holds at most `capacity` characters. When a write does not fit, the oldest characters are dropped so that the buffer ends up with `offset` characters of free space, which avoids shifting the array on every following write; a single write larger than `capacity - offset` keeps only its last `capacity - offset` characters. The defaults are 20,000 and 2,000. `LRUCachedOutputStream.of([out,] buffer)` tees an output stream into such a buffer, for example to show the last lines of a process output in an error message.

Sample: `doc_examples/util/IOExamples.java` (`testLRUCharBuffer`)

```java
LRUCharBuffer buffer = new LRUCharBuffer.MemoryCharBuffer(10, 2);   // capacity 10, slack 2
buffer.write("12345678");
buffer.write("abc");                  // overflow: oldest characters dropped
assertEquals("45678abc", buffer.toString());
buffer.write("ABCDEFGHIJKLMNOPQRST");
assertEquals("MNOPQRST", buffer.toString());   // at most capacity-slack characters kept after a trim

// Tee an output stream into a buffer, e.g. to keep the tail of a process output
LRUCharBuffer tail = new LRUCharBuffer.MemoryCharBuffer(5, 0);
ByteArrayOutputStream all = new ByteArrayOutputStream();
try (LRUCachedOutputStream os = LRUCachedOutputStream.of(all, tail)) {
    os.write("hello world".getBytes(StandardCharsets.UTF_8));
}
assertEquals("hello world", all.toString(StandardCharsets.UTF_8));
assertEquals("world", tail.toString());

NullOutputStream.instance.write(new byte[100]);   // discards everything
```
