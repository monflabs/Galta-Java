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

Every filesystem has a `newBuilder()`. `build()` creates a new, independent filesystem each time: two memory filesystems never share files. All of them support the `basic` attribute view only, and none supports `WatchService` or `UserPrincipalLookupService`.

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

Closing a filesystem makes it unusable: `getPath()` and the other operations throw `ClosedFileSystemException`, and `isOpen()` returns `false`.

Sample: `doc_examples/filesystem/FileSystemsExamples.java` (`testClosedFileSystem`)

## Builders

| Builder | Options |
|---|---|
| `MemoryFileSystem.newBuilder()` | `provider(...)`, `uri(...)` |
| `FileFileSystem.newBuilder()` | `root(File)`, `provider(...)`, `uri(...)` |
| `PathFileSystem.newBuilder()` | `root(Path)`, `provider(...)`, `uri(...)` |
| `PathDelegatingFileSystem.newBuilder()` | `root(Path)` (required), `provider(...)`, `uri(...)` |
| `ZipFileSystem.newBuilder()` | `zipFile(Path)` (required), `provider(...)`, `uri(...)` |
| `ResourceFileSystem.newBuilder()` | `root(String)`, `manifest(String)`, `classLoader(ClassLoader)`, `provider(...)`, `uri(...)` |

Without `root`, `FileFileSystem` and `PathFileSystem` see the whole disk. With a root, the root must exist (`FileFileSystem` also checks that it is a directory); `build()` wraps the `IOException` in a `FileSystemRuntimeException` otherwise. `FileFileSystem` expands a leading `~` in the root to the user home folder.

## Sandboxes

`FileFileSystem`, `PathFileSystem` and `PathDelegatingFileSystem` confine every path to their root:

- The root is `/`. Paths are resolved against it, and `..` is resolved lexically first and never climbs above it, like a chroot: `/../notes.txt` is `/notes.txt`.
- A symbolic link inside the root that points outside is refused with `AccessDeniedException`, on reads and on writes.
- A sibling folder whose name starts with the root's name (`/data/sb2` next to `/data/sb`) is not inside the root.

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

Both expose a folder of the local disk. `FileFileSystem` goes through `java.io.File`, uses the OS separator, and `toFile()` works. `PathFileSystem` goes through `java.nio`, always uses `/` whatever the platform, and converts to the real path with `toOSPath(String)`. With a root, its `toUri()` is a `pathfs:` URI of the virtual path (like a sandboxed `FileFileSystem`), so it never discloses the host path; without one, it is the URI of the real file.

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

`ZipFileSystem` and `ResourceFileSystem` report `isReadOnly()` as `true`. Opening a file for writing, creating a directory, deleting, moving, and copying within them throw `ReadOnlyFileSystemException`; copying a file out to another filesystem works.

### ZIP files

A folder that has no entry of its own in the archive but contains entries is still seen as a directory. Entries are read fully into memory when opened. Closing the filesystem closes the ZIP file.

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

A class loader cannot list its resources, so `ResourceFileSystem` reads the list from a manifest file. The manifest is loaded from `<root>/resources.manifest` on the classpath (`manifest(...)` changes the file name); every copy found on the classpath is merged. Each line holds one path relative to the root; `#` starts a comment, blank lines are ignored, and anything after a tab on a line is ignored. Folders are implied by the paths. An excerpt of the manifest used by the sample:

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
| `{a,b}` | Either alternative (groups can nest) |
| `[abc]`, `[a-z]`, `[!abc]` | A character class; `!` negates |
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

The ZIP and resource filesystems have their own matchers, which drop the leading `/` before matching: write patterns relative to the root.

Sample: `doc_examples/filesystem/FileSystemsExamples.java` (`testZipGlobIsRelative`)

```java
try (FileSystem fs = ZipFileSystem.newBuilder().zipFile(zip).build()) {
    // Zip (and resource) matchers drop the leading "/" before matching
    fs.getPathMatcher("glob:src/*.java").matches(fs.getPath("/src/Main.java"));    // true
    fs.getPathMatcher("glob:/src/*.java").matches(fs.getPath("/src/Main.java"));   // false
}
```

## URIs and providers

`Path.toUri()` uses the provider's scheme, with the characters that need it encoded. The providers are not registered with the JDK (the module has no `META-INF/services` entry), so `Path.of(URI)` and `FileSystems.newFileSystem(URI, ...)` do not find them: build filesystems with the builders, or call a provider instance directly.

Each class has a shared default provider that does not keep track of the filesystems it creates. A provider constructed with `registered = true` remembers them by the scheme and authority of their URI (the path names a file inside a filesystem, so `getPath(uri)` finds the filesystem of any of its files' URIs; zip and resource URIs also carry the filesystem in their path, before the `!`): `getFileSystem(uri)` finds an open one, creating a second one for the same URI throws `FileSystemAlreadyExistsException`, and closing a filesystem unregisters it.

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
