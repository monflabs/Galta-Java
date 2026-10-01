# File Systems

The `filesystem` module (`org.monflabs.galta:filesystem`, package `org.monflabs.filesystem`) implements `java.nio.file.FileSystem` over an in-memory tree, a sandboxed folder, a ZIP file and classpath resources. Once built, a filesystem is used through the standard `Files` and `Path` APIs, so the same code can run against a real folder in production and a memory filesystem in a test.

## The providers

| Filesystem | Package | Backed by | Writable | Separator | URI scheme |
|---|---|---|---|---|---|
| `MemoryFileSystem` | `memory` | A tree of nodes in memory | yes | `/` | `memory` |
| `FileFileSystem` | `file` | `java.io.File`, optionally sandboxed to a root folder | yes | the OS separator | `file-impl` |
| `PathFileSystem` | `path` | The default `java.nio` filesystem, optionally sandboxed to a root folder | yes | always `/` | `pathfs` |
| `PathDelegatingFileSystem` | `delegate` | Any existing `Path` used as the root, on any filesystem | yes | the delegate's separator | `path-delegate` |
| `ZipFileSystem` | `zip` | A `.zip` file | no | `/` | `zip` |
| `ResourceFileSystem` | `resources` | Classpath resources listed in a manifest | no | `/` | `resource` |

Every filesystem has a `newBuilder()`. `build()` creates a new, independent filesystem each time: two memory filesystems never share files. None supports `WatchService`, `UserPrincipalLookupService`, symbolic or hard link creation, or `FileChannel`.

Attribute views: the memory, ZIP and resource filesystems support the `basic` view only (`supportedFileAttributeViews()` is `[basic]`, another view is `null` from `getFileAttributeView()` and `UnsupportedOperationException` from `readAttributes()`). The file, path and delegating filesystems support the views of the filesystem underneath them (`posix`, `owner`, `dos`... on the default filesystem), through the `Class` form, the `String` form (`"posix:permissions"`) and `getFileAttributeView()` alike; with a sandbox, every call of such a view checks the path again.

Sample: `doc_examples/filesystem/FileSystemsExamples.java` (`testMemoryFileSystem`, `testEachBuildIsIndependent`)

```java
try (FileSystem fs = MemoryFileSystem.newBuilder().build()) {
    Path docs = fs.getPath("/docs");
    Files.createDirectories(docs);
    Files.writeString(docs.resolve("a.txt"), "Hello", StandardCharsets.UTF_8);
    Files.writeString(fs.getPath("/docs/b.txt"), "World", StandardCharsets.UTF_8);

    Files.readString(fs.getPath("/docs/a.txt"));   // "Hello"
    try (Stream<Path> s = Files.list(docs)) {
        List<String> names = s.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
        // [a.txt, b.txt]
    }
    fs.getSeparator();   // "/"
    fs.isReadOnly();     // false
}
```

`Files.walk`, `Files.copy` and the other `Files` helpers work as usual:

Sample: `doc_examples/filesystem/FileSystemsExamples.java` (`testWalk`)

```java
try (FileSystem fs = MemoryFileSystem.newBuilder().build()) {
    Files.createDirectories(fs.getPath("/a/b"));
    Files.writeString(fs.getPath("/a/b/c.txt"), "c");
    Files.copy(fs.getPath("/a/b/c.txt"), fs.getPath("/a/d.txt"));
    try (Stream<Path> s = Files.walk(fs.getPath("/"))) {
        List<String> all = s.map(Path::toString).sorted().collect(Collectors.toList());
        // [/, /a, /a/b, /a/b/c.txt, /a/d.txt]
    }
}
```

Closing a filesystem makes it unusable: `getPath()`, `getRootDirectories()`, `getFileStores()` and every operation on its files throw `ClosedFileSystemException`, also through a `Path` obtained before the filesystem was closed (only the lexical `Path` methods, such as `resolve()` or `normalize()`, keep working). `isOpen()` returns `false`.

Sample: `doc_examples/filesystem/FileSystemsExamples.java` (`testClosedFileSystem`)

## Builders

| Builder | Options |
|---|---|
| `MemoryFileSystem.newBuilder()` | `provider(...)`, `uri(...)` |
| `FileFileSystem.newBuilder()` | `root(File)`, `provider(...)`, `uri(...)` |
| `PathFileSystem.newBuilder()` | `root(Path)`, `provider(...)`, `uri(...)` |
| `PathDelegatingFileSystem.newBuilder()` | `root(Path)` (required), `provider(...)`, `uri(...)` |
| `ZipFileSystem.newBuilder()` | `zipFile(Path)` (required), `maxEntrySize(long)`, `provider(...)`, `uri(...)` |
| `ResourceFileSystem.newBuilder()` | `root(String)`, `manifest(String)`, `classLoader(ClassLoader)`, `maxEntrySize(long)`, `provider(...)`, `uri(...)` |

Without `root`, `FileFileSystem` and `PathFileSystem` see the whole disk. With a root, the root must exist and be a directory; `build()` wraps the `IOException` in a `FileSystemRuntimeException` otherwise. `FileFileSystem` expands a root of `~` or starting with `~/` to the user home folder (`~name` is an ordinary folder name).

Without a root, a relative path is resolved differently: against the working directory by `FileFileSystem` (like `java.io.File`), against `/` by `PathFileSystem`. With a root, both resolve it against the root.

## Sandboxes

`FileFileSystem`, `PathFileSystem` and `PathDelegatingFileSystem` confine every path to their root:

- The root is `/`. Paths are resolved against it, and `..` is resolved lexically first and never climbs above it, like a chroot: `/../notes.txt` is `/notes.txt`.
- A symbolic link inside the root that points outside is refused with `AccessDeniedException`, on reads and on writes.
- A sibling folder whose name starts with the root's name (`/data/sb2` next to `/data/sb`) is not inside the root.
- An operation that does not follow the last element of its path only checks the parent: reading the attributes with `LinkOption.NOFOLLOW_LINKS` (`Files.isSymbolicLink()`, `Files.walk()`...), deleting, moving, and copying with `NOFOLLOW_LINKS`. A link pointing outside can be inspected, deleted, moved or copied as a link; it is never followed. A stray link therefore no longer breaks `Files.walk()`.
- Errors report the paths of the filesystem, not where the root lives on the host.

The sandbox keeps a well-behaved caller inside its root; it is **not a security boundary**:

- The checks run before the operation. Another process, or another thread, writing in the root at the same time can replace a checked folder with a symbolic link between the check and the operation (a time-of-check/time-of-use race) and make the operation act outside the root. Plain `java.nio` has no `openat()`/`O_NOFOLLOW` style API to close that window.
- A hard link inside the root to a file outside it looks like any other file: it is read and written.

When the content of the root, or concurrent local writers, are not trusted, use operating system isolation (a container, a dedicated user, a chroot).

Sample: `doc_examples/filesystem/FileSystemsExamples.java` (`testFileFileSystemSandbox`, `testFileFileSystemRootMustExist`)

```java
File root = sandbox();
try (FileSystem fs = FileFileSystem.newBuilder().root(root).build()) {
    Files.writeString(fs.getPath("/notes.txt"), "inside");
    new File(root, "notes.txt").exists();                        // true

    // ".." never climbs above the root: "/../notes.txt" is "/notes.txt"
    Files.readString(fs.getPath("/../notes.txt"));               // "inside"
    Files.writeString(fs.getPath("/../../escape.txt"), "x");
    new File(root, "escape.txt").exists();                       // true
    new File(root.getParentFile(), "escape.txt").exists();       // false

    // A sandboxed path has a "file-impl" URI relative to the root
    fs.getPath("/notes.txt").toUri();                            // file-impl:///notes.txt
    fs.getPath("/notes.txt").toFile();                           // new File(root, "notes.txt")
}
```

Sample: `doc_examples/filesystem/FileSystemsExamples.java` (`testSymlinkEscapeIsRejected`)

```java
try (FileSystem fs = PathFileSystem.newBuilder().root(root.toPath()).build()) {
    // The link lives inside the root but points outside: refused
    Files.readString(fs.getPath("/link/secret.txt"));   // throws AccessDeniedException
}
```

### FileFileSystem or PathFileSystem

Both expose a folder of the local disk. `FileFileSystem` goes through `java.io.File`, uses the OS separator, and `toFile()` works. `PathFileSystem` goes through `java.nio`, always uses `/` whatever the platform, and converts to the real path with `toOSPath(String)`. With a root, its `toUri()` is a `pathfs:` URI of the virtual path (like a sandboxed `FileFileSystem`), so it never discloses the host path; without one, it is a `pathfs:` URI of the host path (see [URIs and providers](#uris-and-providers)).

Sample: `doc_examples/filesystem/FileSystemsExamples.java` (`testPathFileSystem`)

```java
try (PathFileSystem fs = PathFileSystem.newBuilder().root(root.toPath()).build()) {
    Path p = fs.getPath("/data/values.txt");
    Files.createDirectories(p.getParent());
    Files.writeString(p, "42");

    fs.getSeparator();                    // "/" on every platform
    fs.toOSPath("/data/values.txt");      // <root>/data/values.txt
    // With a root, toUri() is a URI of the virtual path: it does not disclose the host path
    p.toUri();                            // pathfs:///data/values.txt
}
```

### PathDelegatingFileSystem

`PathDelegatingFileSystem` turns any directory `Path` into the root of a new filesystem, whatever filesystem that path belongs to. The root must exist and be a directory (otherwise `NoSuchFileException` or `NotDirectoryException`, wrapped in a `FileSystemRuntimeException` by the builder). Over a memory filesystem, it gives each tenant its own confined view:

Sample: `doc_examples/filesystem/FileSystemsExamples.java` (`testPathDelegatingOverMemory`)

```java
try (FileSystem memory = MemoryFileSystem.newBuilder().build()) {
    Path tenant = memory.getPath("/tenants/acme");
    Files.createDirectories(tenant);
    try (FileSystem fs = PathDelegatingFileSystem.newBuilder().root(tenant).build()) {
        Files.writeString(fs.getPath("/config.json"), "{}");
        Files.exists(memory.getPath("/tenants/acme/config.json"));   // true
        // Same chroot rule: ".." stays at the delegate root
        Files.exists(fs.getPath("/../config.json"));                 // true
    }
}
```

## Read-only filesystems

`ZipFileSystem` and `ResourceFileSystem` report `isReadOnly()` as `true`. Opening a file for writing, creating a directory, deleting, moving, and copying within them (or to another read-only filesystem, whatever the options) throw `ReadOnlyFileSystemException`; copying a file out to another filesystem works.

`Files.newInputStream()` (and so `Files.copy()` out, `Files.lines()`, `Files.newBufferedReader()`) streams a file of any size. A random-access channel (`Files.newByteChannel()`, and the helpers built on it: `Files.readAllBytes()`, `Files.readString()`, `Files.readAllLines()`) holds the whole file in memory, up to `maxEntrySize` bytes (256 MB by default, `maxEntrySize(...)` on the builder or the `maxEntrySize` environment parameter): a larger file, or a "zip bomb" inflating beyond the limit, throws a `FileSystemException` that says to use `Files.newInputStream()`.

Two paths are the same file (`Files.isSameFile()`, the `fileKey` attribute) when their normalized absolute paths are equal: `/a/../b.txt` is `/b.txt`.

### ZIP files

A folder that has no entry of its own in the archive but contains entries is still seen as a directory. An entry with an unsafe name (`/abs`, `../x`, `a/./b`, `a//b`, a backslash) is skipped. When the archive holds several entries with the same name, the one `ZipFile.getEntry()` returns is used, for the attributes and the content alike. A file entry `f` next to entries under `f/` is a directory (its children are listed and read). Closing the filesystem closes the ZIP file.

Sample: `doc_examples/filesystem/FileSystemsExamples.java` (`testZipFileSystem`)

```java
try (FileSystem fs = ZipFileSystem.newBuilder().zipFile(zip).build()) {
    fs.isReadOnly();                                   // true
    Files.readString(fs.getPath("/readme.txt"));       // "read me"
    // "src" has no entry of its own but is seen as a directory
    Files.isDirectory(fs.getPath("/src"));             // true
    try (Stream<Path> s = Files.list(fs.getPath("/"))) {
        // [readme.txt, src]
    }
    Files.writeString(fs.getPath("/new.txt"), "x");    // throws ReadOnlyFileSystemException
    Files.delete(fs.getPath("/readme.txt"));           // throws ReadOnlyFileSystemException

    // Copying out to another filesystem is fine
    try (FileSystem memory = MemoryFileSystem.newBuilder().build()) {
        Files.copy(fs.getPath("/readme.txt"), memory.getPath("/readme.txt"));
        Files.readString(memory.getPath("/readme.txt"));   // "read me"
    }
}
```

### Classpath resources

A class loader cannot list its resources, so `ResourceFileSystem` reads the list from a manifest file. The manifest is loaded from `<root>/resources.manifest` on the classpath (`manifest(...)` changes the file name); every copy found on the classpath is merged. Each line holds one path relative to the root (a leading `/` is dropped); a line starting with `#` is a comment, blank lines are ignored, and anything after a tab on a line is ignored. Folders are implied by the paths. A path with a `.` or `..` segment, an empty segment (`a//b`, `a/`) or a backslash is skipped, as for ZIP entries. An excerpt of the manifest used by the sample:

```
# Configuration files
config/app.properties
config/database.properties

# Email templates
templates/email/welcome.html
templates/email/goodbye.html
templates/web/home.html
```

The class loader defaults to the thread context class loader.

Sample: `doc_examples/filesystem/FileSystemsExamples.java` (`testResourceFileSystem`)

```java
// Reads test-resources/resources.manifest from the classpath
try (FileSystem fs = ResourceFileSystem.newBuilder().root("test-resources").build()) {
    fs.isReadOnly();                                                // true
    String props = Files.readString(fs.getPath("/config/app.properties"));
    props.contains("app.version=1.0.0");                            // true
    Files.isDirectory(fs.getPath("/templates/email"));              // true
    try (Stream<Path> s = Files.list(fs.getPath("/templates"))) {
        // [email, web]
    }
}
```

A missing manifest makes `build()` fail (`NoSuchFileException` wrapped in a `FileSystemRuntimeException`). Only files listed in the manifest exist, even if the class loader has more.

## Glob and regex matching

`getPathMatcher()` accepts `glob:` and `regex:`. The memory, file, path and delegating filesystems share one glob implementation that matches the whole path string, so an absolute pattern only matches absolute paths:

| Glob | Matches |
|---|---|
| `*` | Any characters except the separator |
| `**` | Any characters, separators included |
| `?` | One character except the separator |
| `{a,b}` | Either alternative (groups can nest, unlike the JDK's globs) |
| `[abc]`, `[a-z]`, `[!abc]` | A character class; `!` negates. A class never matches the separator: `[/]` is a `PatternSyntaxException`, `[!x]` does not match `/` |
| `\x` | The character `x` literally |
| `/` | The filesystem separator |

Sample: `doc_examples/filesystem/FileSystemsExamples.java` (`testGlob`)

```java
try (FileSystem fs = MemoryFileSystem.newBuilder().build()) {
    PathMatcher txtInDocs = fs.getPathMatcher("glob:/docs/*.txt");
    txtInDocs.matches(fs.getPath("/docs/a.txt"));        // true
    txtInDocs.matches(fs.getPath("/docs/sub/a.txt"));    // false: * stops at "/"
    txtInDocs.matches(fs.getPath("docs/a.txt"));         // false: matched against the full path string

    PathMatcher anyTxt = fs.getPathMatcher("glob:**.txt");
    anyTxt.matches(fs.getPath("/docs/sub/a.txt"));       // true: ** crosses "/"

    PathMatcher alt = fs.getPathMatcher("glob:/src/*.{java,kt}");
    alt.matches(fs.getPath("/src/Main.kt"));             // true
    alt.matches(fs.getPath("/src/Main.scala"));          // false

    PathMatcher cls = fs.getPathMatcher("glob:/v[0-9]/file?.[!b]*");
    cls.matches(fs.getPath("/v1/file1.a"));              // true
    cls.matches(fs.getPath("/v1/file1.b"));              // false

    PathMatcher regex = fs.getPathMatcher("regex:/logs/\\d+\\.log");
    regex.matches(fs.getPath("/logs/2024.log"));         // true
}
```

An empty pattern (`glob:` or `regex:`) matches the empty path only.

The ZIP and resource filesystems have their own matchers, which drop the leading `/` before matching: write patterns relative to the root.

Sample: `doc_examples/filesystem/FileSystemsExamples.java` (`testZipGlobIsRelative`)

```java
try (FileSystem fs = ZipFileSystem.newBuilder().zipFile(zip).build()) {
    // Zip (and resource) matchers drop the leading "/" before matching
    fs.getPathMatcher("glob:src/*.java").matches(fs.getPath("/src/Main.java"));    // true
    fs.getPathMatcher("glob:/src/*.java").matches(fs.getPath("/src/Main.java"));   // false
}
```

## Path syntax

Paths follow the JDK's own rules (those of a Unix path for the `/` filesystems):

- The empty path `""` has one, empty, name: `getNameCount()` is 1 and `getFileName()` is the empty path. `normalize()` turns `.` and `a/..` into the empty path. Resolved against the root, the empty path is the root.
- `relativize()` normalizes both paths first, and a base that is left with `..` names cannot be relativized (`IllegalArgumentException`, as with the JDK: `../a` has no relative path to `b`). For any `p` and `q`, `p.resolve(p.relativize(q)).normalize()` equals `q.normalize()`.
- On a `/` filesystem a backslash is an ordinary character of a file name, as for the JDK on Unix: `a\b` is one name. On a filesystem whose separator is a backslash (a `FileFileSystem` on Windows, or a `PathDelegatingFileSystem` over one), `/` is a separator too. `PathFileSystem` uses `/` everywhere and treats a backslash as a separator only on a Windows host, where it cannot be part of a file name.
- A path of another provider cannot be combined with one of these: `resolve()` and `relativize()` throw `ProviderMismatchException`, `compareTo()` throws `ClassCastException`, `startsWith()`/`endsWith()` return `false`. A name holding a NUL character throws `InvalidPathException`.

The memory filesystem resolves `.` and `..` against its actual nodes, as an operating system does: `/d/x/../y` names `/d/y` only when `/d/x` is an existing directory (otherwise `NoSuchFileException`, or a `FileSystemException` "Not a directory" when it is a file). The sandboxed filesystems resolve `..` lexically first instead (see [Sandboxes](#sandboxes)): `..` never climbs above their root.

## The memory filesystem

- A move within a memory filesystem renames the node itself: nothing is copied, a channel opened before the move keeps reading and writing the moved file, and its `fileKey` does not change. A move to another memory filesystem copies the tree and deletes the source.
- Channels follow the JDK's option rules: `READ` with `APPEND`, or `APPEND` with `TRUNCATE_EXISTING`, is an `IllegalArgumentException`; `CREATE` is ignored for a channel opened for reading only; `DELETE_ON_CLOSE` deletes the file when the channel is closed; writing nothing past the end does not grow the file. Initial `FileAttribute`s (`Files.createFile(path, attrs)`) are an `UnsupportedOperationException`. `Files.copy()` with `ATOMIC_MOVE` is an `UnsupportedOperationException`.
- A directory is executable (searchable), a name starting with `.` is hidden, and `Files.isSameFile()` of two different missing paths is a `NoSuchFileException`, as on Unix.
- The `FileStore` reports the maximum heap size as its total space, and the total minus the bytes held by the files (a running total) as its usable space; truncating a large file gives its memory back.

## URIs and providers

`Path.toUri()` uses the provider's scheme, with the characters that need it encoded, for every filesystem - also for an unsandboxed `FileFileSystem` (`file-impl:///home/me/a.txt`) or `PathFileSystem` (`pathfs:///home/me/a.txt`), whose URIs used to be `file:` URIs that their provider could not map back. The URI keeps the authority of the filesystem's URI (`memory://tenant1/a.txt`), so it never names a file of another filesystem. ZIP and resource URIs also carry the filesystem in their path, before the `!`: the archive's path (`zip:///home/me/a.zip!/readme.txt`) and the root (`resource:///test-resources!/config/app.properties`); the builders use that identity as the filesystem's URI when none is given.

The providers are not registered with the JDK (the module has no `META-INF/services` entry), so `Path.of(URI)` and `FileSystems.newFileSystem(URI, ...)` do not find them: build filesystems with the builders, or call a provider instance directly.

Each class has a shared default provider that does not keep track of the filesystems it creates. A provider constructed with `registered = true` remembers them by the scheme and authority of their URI (and, for ZIP and resource URIs, the part of the path before the `!`). The path names a file inside a filesystem, so the provider's `getPath(uri)` finds the filesystem of any of its files' URIs and maps the URI back to the path. `getFileSystem(uri)` finds an open one, creating a second one for the same URI throws `FileSystemAlreadyExistsException`, and closing a filesystem unregisters it.

Sample: `doc_examples/filesystem/FileSystemsExamples.java` (`testUris`)

```java
try (FileSystem fs = MemoryFileSystem.newBuilder().build()) {
    fs.getPath("/docs/a b.txt").toUri();   // memory:///docs/a%20b.txt
}

// A provider created with registered=true remembers its filesystems by URI
MemoryFileSystemProvider provider = new MemoryFileSystemProvider(true);
URI uri = URI.create("memory:///store");
try (FileSystem fs = MemoryFileSystem.newBuilder().provider(provider).uri(uri).build()) {
    provider.getFileSystem(uri);   // fs
    MemoryFileSystem.newBuilder().provider(provider).uri(uri).build();   // throws FileSystemAlreadyExistsException
}
// Closing unregisters it
provider.getFileSystem(uri);       // throws FileSystemNotFoundException
```
