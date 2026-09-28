# Path-Delegating FileSystem - Summary

## What is it?

A new FileSystem implementation that delegates to **any Path** as its root. This is the most flexible of the three implementations because it works with any filesystem.

## Why is it useful?

Unlike the File-based implementation (which only works with `java.io.File`), the Path-delegating implementation can sandbox **any** filesystem:

- ✅ Default filesystem (`Paths.get("/...")`)
- ✅ Memory filesystem (`memoryFS.getPath("/...")`)
- ✅ ZIP/JAR filesystems
- ✅ Custom filesystems
- ✅ Even another Path-delegating filesystem (nested!)

## Quick Example

### Basic Usage

```java
// Any Path can be the root!
Path root = Paths.get("/home/user/myproject");

PathDelegatingFileSystemProvider provider = new PathDelegatingFileSystemProvider();
Map<String, Object> env = new HashMap<>();
env.put(PathDelegatingFileSystemProvider.ROOT_PATH_PARAM, root);

try (FileSystem fs = provider.newFileSystem(
        URI.create("path-delegate:///sandbox"), env)) {
    
    // "/" now points to /home/user/myproject
    Path file = fs.getPath("/src/Main.java");
    // Accesses: /home/user/myproject/src/Main.java
    
    String content = new String(Files.readAllBytes(file));
}
```

### Delegating to Memory FileSystem

```java
// Create memory filesystem
MemoryFileSystemProvider memProvider = new MemoryFileSystemProvider();
FileSystem memFs = memProvider.newFileSystem(
    URI.create("memory:///test"), new HashMap<>()
);

// Create structure
Path memRoot = memFs.getPath("/app-data");
Files.createDirectories(memRoot);
Files.write(memRoot.resolve("config.txt"), "data".getBytes());

// Now delegate to it
PathDelegatingFileSystemProvider delProvider = new PathDelegatingFileSystemProvider();
Map<String, Object> env = new HashMap<>();
env.put(PathDelegatingFileSystemProvider.ROOT_PATH_PARAM, memRoot);

try (FileSystem delFs = delProvider.newFileSystem(
        URI.create("path-delegate:///mem"), env)) {
    
    // "/" in delFs = /app-data in memFs
    Path config = delFs.getPath("/config.txt");
    String data = new String(Files.readAllBytes(config));
    System.out.println(data); // "data"
}
```

### Nested Delegation

```java
// Real filesystem
Path realRoot = Paths.get("/projects");

// First delegate
FileSystem fs1 = createDelegate(realRoot.resolve("project-a"));

// Second delegate (delegates to first delegate!)
Path srcPath = fs1.getPath("/src");
PathDelegatingPath delPath = (PathDelegatingPath) srcPath;
FileSystem fs2 = createDelegate(delPath.toDelegatePath());

// fs2's "/" = /projects/project-a/src
```

## Key Differences from File-based

| Feature | File-based | Path-delegating |
|---------|-----------|-----------------|
| Root type | `java.io.File` | Any `Path` |
| Works with | Real filesystem only | Any filesystem |
| toFile() | ✅ Supported | ❌ Not supported |
| Composable | No | Yes (nestable) |
| Use case | Sandbox real files | Sandbox anything |

## Components

### PathDelegatingFileSystemProvider
- Scheme: `path-delegate://`
- Requires `ROOT_PATH_PARAM` in environment
- Validates root exists and is a directory
- All operations delegate to underlying Path's filesystem

### PathDelegatingFileSystem
- Stores reference to root Path
- Single virtual root "/"
- `getRootPath()` - returns the delegate root
- `getDelegateInfo()` - shows what it's delegating to

### PathDelegatingPath
- Extends AbstractPath (shares path manipulation code)
- `toDelegatePath()` - converts to underlying Path
- Cannot convert to File (filesystem-agnostic)
- Resolves all paths relative to delegate root

## Security

All operations validate paths stay within the root:

```java
Path escape = fs.getPath("/../../../etc/passwd");
Files.readAllBytes(escape);
// Throws: AccessDeniedException: Path escapes filesystem root
```

Uses `toRealPath()` to resolve symlinks and `..` for security.

## Use Cases

### 1. Testing with Different Backends

```java
// Test with memory (fast)
@Test
void testWithMemory() {
    MemoryFileSystem memFs = createMemoryFS();
    Path testRoot = memFs.getPath("/test");
    FileSystem testFs = createDelegate(testRoot);
    
    myApp.setFileSystem(testFs);
    myApp.run();
    
    // Verify results
}

// Same test with real files (integration)
@Test
void testWithRealFiles() {
    Path realRoot = Files.createTempDirectory("test");
    FileSystem testFs = createDelegate(realRoot);
    
    myApp.setFileSystem(testFs);
    myApp.run();
    
    // Verify results on disk
}
```

### 2. Multi-tenant Isolation

```java
public FileSystem createTenantFS(String tenantId, Path basePath) {
    Path tenantRoot = basePath.resolve(tenantId);
    Files.createDirectories(tenantRoot);
    
    Map<String, Object> env = new HashMap<>();
    env.put(ROOT_PATH_PARAM, tenantRoot);
    
    return provider.newFileSystem(
        URI.create("path-delegate:///" + tenantId), 
        env
    );
}

// Tenant A can only see their data
FileSystem tenantA = createTenantFS("tenant-a", Paths.get("/data"));
// "/" = /data/tenant-a
```

### 3. Archive Access

```java
// Open ZIP file
Path zipPath = Paths.get("/archives/data.zip");
FileSystem zipFs = FileSystems.newFileSystem(zipPath, null);

// Delegate to subdirectory within ZIP
Path zipSubdir = zipFs.getPath("/exports/2024");

Map<String, Object> env = new HashMap<>();
env.put(ROOT_PATH_PARAM, zipSubdir);

try (FileSystem delFs = createDelegate(zipSubdir)) {
    // "/" = /exports/2024 inside the ZIP file
    try (DirectoryStream<Path> stream = 
            Files.newDirectoryStream(delFs.getPath("/"))) {
        for (Path entry : stream) {
            System.out.println(entry);
        }
    }
}
```

## Comparison of All Three

| | File | Memory | Path-delegate |
|---|------|--------|---------------|
| **Root** | File/String | In-memory | Any Path |
| **Persistence** | Yes | No | Depends |
| **Speed** | Disk I/O | Fast | Depends |
| **Sandboxing** | Optional | N/A | Always |
| **Flexible** | No | No | Yes |
| **toFile()** | ✅ | ❌ | ❌ |
| **Composable** | No | No | ✅ |

## Performance

- Small overhead (~5ms creation, ~10% runtime)
- Delegates directly to underlying filesystem
- No buffering or caching (transparent delegation)

## Documentation

See **PATH-DELEGATING.md** for:
- Complete API documentation
- More usage examples
- Security details
- Best practices

See **COMPARISON.md** for:
- Side-by-side comparison of all three
- Performance benchmarks
- Decision guide
- Migration examples

## Running the Demo

```bash
javac *.java
java PathDelegatingDemo
```

The demo shows:
1. Delegating to file-based path
2. Delegating to memory-based path
3. Nested delegation (delegate of delegate!)

## Summary

The Path-delegating filesystem is the **most flexible** implementation:

✅ Works with **any** Path/FileSystem  
✅ **Composable** (can nest delegates)  
✅ **Secure** (always sandboxed)  
✅ **Clean** (pure NIO.2, no File dependency)  

Use it when you need maximum flexibility and don't want to be tied to a specific filesystem implementation!
