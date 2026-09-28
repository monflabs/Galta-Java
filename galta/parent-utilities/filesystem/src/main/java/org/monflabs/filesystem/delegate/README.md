# Path-Delegating FileSystem

A flexible FileSystem implementation that delegates to any `Path` as its root. This allows creating sandboxed views of any filesystem (File, Memory, Zip, or even another delegating filesystem).

## Overview

The Path-delegating filesystem is the most flexible of the three implementations:

| Implementation | Root Type | Use Case |
|---------------|-----------|----------|
| **File-based** | `java.io.File` | Sandbox real filesystem |
| **Memory-based** | In-memory | Testing, temporary data |
| **Path-delegating** | Any `Path` | Universal sandboxing |

## Key Features

✅ **Universal**: Works with any FileSystem's Path  
✅ **Sandboxed**: Restricts access to subtree  
✅ **Composable**: Can delegate to another delegate  
✅ **Secure**: Validates paths stay within root  
✅ **Flexible**: No dependency on java.io.File  

## Architecture

```
PathDelegatingFileSystemProvider
    └─> Uses any Path as root
    └─> Can delegate to:
        ├─> Default FileSystem (via Paths.get())
        ├─> MemoryFileSystem (in-memory)
        ├─> FileFileSystem (File-based)
        ├─> ZipFileSystem (JDK built-in)
        └─> Another PathDelegatingFileSystem (nested!)
```

## Usage

### Basic Example: Delegate to File System

```java
// Create a Path in the default filesystem
Path realDir = Paths.get("/home/user/myproject");

// Create delegating filesystem
PathDelegatingFileSystemProvider provider = new PathDelegatingFileSystemProvider();
URI uri = URI.create("path-delegate:///myfs");

Map<String, Object> env = new HashMap<>();
env.put(PathDelegatingFileSystemProvider.ROOT_PATH_PARAM, realDir);

try (FileSystem fs = provider.newFileSystem(uri, env)) {
    // "/" now points to /home/user/myproject
    Path file = fs.getPath("/src/Main.java");
    // Actually accesses: /home/user/myproject/src/Main.java
    
    String content = new String(Files.readAllBytes(file));
    System.out.println(content);
}
```

### Example: Delegate to Memory FileSystem

```java
// Create memory filesystem
MemoryFileSystemProvider memProvider = new MemoryFileSystemProvider();
FileSystem memFs = memProvider.newFileSystem(
    URI.create("memory:///test"), 
    new HashMap<>()
);

// Create structure in memory
Path memRoot = memFs.getPath("/app-data");
Files.createDirectories(memRoot);
Files.write(memRoot.resolve("config.txt"), "key=value".getBytes());

// Delegate to the memory path
PathDelegatingFileSystemProvider delProvider = new PathDelegatingFileSystemProvider();

Map<String, Object> env = new HashMap<>();
env.put(PathDelegatingFileSystemProvider.ROOT_PATH_PARAM, memRoot);

try (FileSystem delFs = delProvider.newFileSystem(
        URI.create("path-delegate:///mem"), env)) {
    
    // "/" now points to /app-data in memory filesystem
    Path config = delFs.getPath("/config.txt");
    String content = new String(Files.readAllBytes(config));
    System.out.println(content); // "key=value"
}

memFs.close();
```

### Example: Nested Delegation

```java
// Real filesystem: /tmp/project
Path realRoot = Paths.get("/tmp/project");
Files.createDirectories(realRoot.resolve("src/main/java"));

// First delegate: points to /tmp/project/src
Map<String, Object> env1 = new HashMap<>();
env1.put(ROOT_PATH_PARAM, realRoot.resolve("src"));
FileSystem fs1 = provider.newFileSystem(
    URI.create("path-delegate:///src"), env1
);

// Second delegate: points to /main within first delegate
// Which is really /tmp/project/src/main
Path mainInFs1 = fs1.getPath("/main");
PathDelegatingPath delPath = (PathDelegatingPath) mainInFs1;
Path mainReal = delPath.toDelegatePath();

Map<String, Object> env2 = new HashMap<>();
env2.put(ROOT_PATH_PARAM, mainReal);
FileSystem fs2 = provider.newFileSystem(
    URI.create("path-delegate:///main"), env2
);

// fs2's "/" now points to /tmp/project/src/main
Path javaFile = fs2.getPath("/java/App.java");
// Actually: /tmp/project/src/main/java/App.java
```

## Components

### PathDelegatingFileSystemProvider

**Scheme**: `path-delegate://`

**Parameters**:
- `ROOT_PATH_PARAM` (required): The Path to use as root

**Features**:
- Validates root exists and is a directory
- Converts to real path for security
- Validates all operations stay within root
- Delegates all I/O to underlying filesystem

### PathDelegatingFileSystem

**Properties**:
- Stores reference to root Path
- Uses root's separator
- Single virtual root "/"

**Methods**:
- `getRootPath()` - Get the underlying root path
- `getDelegateInfo()` - Get info about delegate

### PathDelegatingPath

**Key Method**:
- `toDelegatePath()` - Convert to underlying Path

**Features**:
- Resolves paths relative to root
- Validates security on toRealPath()
- Cannot convert to File (no assumptions about underlying FS)

## Path Resolution

### Virtual to Real Mapping

```
Root Path: /home/user/project/src

Virtual Path          Real Path
/                  →  /home/user/project/src
/main              →  /home/user/project/src/main
/main/App.java     →  /home/user/project/src/main/App.java
/test/../main      →  /home/user/project/src/main (normalized)
```

### Security Validation

All operations validate paths using `toRealPath()`:

```java
private void validatePath(PathDelegatingPath path) throws IOException {
    Path realDelegate = path.toDelegatePath().toRealPath();
    Path realRoot = rootPath.toRealPath();
    
    if (!realDelegate.startsWith(realRoot)) {
        throw new AccessDeniedException("Path escapes root");
    }
}
```

## Comparison with Other Implementations

### vs File-based FileSystem

| Aspect | File-based | Path-delegating |
|--------|-----------|-----------------|
| Root type | `java.io.File` | Any `Path` |
| Scope | Real filesystem only | Any filesystem |
| Dependencies | java.io.File API | java.nio.file.Path API |
| Use case | Real file sandboxing | Universal sandboxing |

### vs Memory FileSystem

| Aspect | Memory | Path-delegating |
|--------|--------|-----------------|
| Storage | In-memory | Delegates to another FS |
| Persistence | No | Depends on delegate |
| Speed | Fast (RAM) | Depends on delegate |
| Use case | Testing, temp data | Sandboxing any FS |

## Use Cases

### 1. Application Isolation

```java
// Each application gets its own sandboxed view
Path appDir = Paths.get("/var/apps/myapp");

Map<String, Object> env = new HashMap<>();
env.put(ROOT_PATH_PARAM, appDir);

FileSystem appFS = provider.newFileSystem(
    URI.create("path-delegate:///app"), env
);

// App sees "/" as /var/apps/myapp
// Cannot access /var/apps/otherapp
```

### 2. Multi-tenant Data Access

```java
public FileSystem createTenantFS(String tenantId) {
    Path tenantData = Paths.get("/data/tenants/" + tenantId);
    
    Map<String, Object> env = new HashMap<>();
    env.put(ROOT_PATH_PARAM, tenantData);
    
    return provider.newFileSystem(
        URI.create("path-delegate:///" + tenantId), 
        env
    );
}

// Tenant A sees only their data
FileSystem tenantA = createTenantFS("tenant-a");
Path file = tenantA.getPath("/documents/report.pdf");
// Real: /data/tenants/tenant-a/documents/report.pdf
```

### 3. Testing with Memory FileSystem

```java
@Test
public void testFileOperations() throws IOException {
    // Create memory filesystem for testing
    MemoryFileSystem memFs = ...;
    Path testRoot = memFs.getPath("/test-data");
    Files.createDirectories(testRoot);
    
    // Create delegate for isolation
    Map<String, Object> env = new HashMap<>();
    env.put(ROOT_PATH_PARAM, testRoot);
    
    try (FileSystem testFs = provider.newFileSystem(
            URI.create("path-delegate:///test"), env)) {
        
        // Test code here - isolated to /test-data
        myApp.setFileSystem(testFs);
        myApp.processFiles();
        
        // Verify results
        assertTrue(Files.exists(testFs.getPath("/output.txt")));
    }
}
```

### 4. Archive File Systems

```java
// Open a ZIP file
Path zipPath = Paths.get("/archives/data.zip");
FileSystem zipFs = FileSystems.newFileSystem(
    zipPath, 
    ClassLoader.getSystemClassLoader()
);

// Create delegate to specific directory in ZIP
Path zipSubdir = zipFs.getPath("/data/exports");

Map<String, Object> env = new HashMap<>();
env.put(ROOT_PATH_PARAM, zipSubdir);

try (FileSystem delFs = provider.newFileSystem(
        URI.create("path-delegate:///zip"), env)) {
    
    // "/" now points to /data/exports inside the ZIP
    try (DirectoryStream<Path> stream = 
            Files.newDirectoryStream(delFs.getPath("/"))) {
        for (Path entry : stream) {
            System.out.println(entry);
        }
    }
}

zipFs.close();
```

### 5. Layer Multiple Sandboxes

```java
// Real FS: /projects
Path projects = Paths.get("/projects");

// Level 1: One project
Path projectA = projects.resolve("project-a");
FileSystem fs1 = createDelegate(projectA);

// Level 2: Source directory within project
Path src = fs1.getPath("/src");
PathDelegatingPath srcDel = (PathDelegatingPath) src;
FileSystem fs2 = createDelegate(srcDel.toDelegatePath());

// Level 3: Main directory within source
Path main = fs2.getPath("/main");
PathDelegatingPath mainDel = (PathDelegatingPath) main;
FileSystem fs3 = createDelegate(mainDel.toDelegatePath());

// fs3's "/" = /projects/project-a/src/main
// Maximum isolation!
```

## Security

### Path Validation

All file operations validate paths:

```java
// This will fail
Path escape = fs.getPath("/../../../etc/passwd");
Files.readAllBytes(escape);
// Throws: AccessDeniedException: Path escapes filesystem root
```

### Real Path Resolution

Uses `toRealPath()` to resolve symlinks and `..`:

```java
// Even with symlinks or tricks
Path tricky = fs.getPath("/data/../../outside.txt");
Files.write(tricky, data);
// Throws: AccessDeniedException (if resolves outside root)
```

### Operations Protected

All these validate the path:
- ✅ Read operations
- ✅ Write operations
- ✅ Directory operations
- ✅ Copy/Move operations
- ✅ Attribute operations

## Advantages

### 1. Filesystem Agnostic

Works with any Path from any FileSystem:
- Default (real files)
- Memory (in-memory)
- ZIP archives
- JAR files
- Custom filesystems

### 2. Composition

Can delegate to another delegate:
```
Real FS → Delegate1 → Delegate2 → Delegate3
```

### 3. No File Dependency

Doesn't require `java.io.File`, works purely with NIO.2:
```java
// Works even if underlying FS doesn't support toFile()
Path memPath = memoryFS.getPath("/data");
FileSystem delFs = createDelegate(memPath); // ✅ Works!
```

### 4. Clean API

Simple to use:
```java
Map<String, Object> env = new HashMap<>();
env.put(ROOT_PATH_PARAM, anyPath);
FileSystem fs = provider.newFileSystem(uri, env);
```

## Limitations

1. **toFile() Not Supported**: Cannot convert to `File` since underlying FS may not support it
2. **Performance Overhead**: Extra layer of indirection
3. **Root Must Exist**: Root path must exist at creation time
4. **Root Must Be Directory**: Cannot delegate to a file

## Best Practices

### 1. Use try-with-resources

```java
try (FileSystem fs = provider.newFileSystem(uri, env)) {
    // Use filesystem
} // Automatically closes
```

### 2. Validate Root Early

```java
if (!Files.exists(rootPath)) {
    throw new IllegalArgumentException("Root does not exist");
}
if (!Files.isDirectory(rootPath)) {
    throw new IllegalArgumentException("Root must be directory");
}
```

### 3. Use toRealPath() for Security

```java
Path rootPath = givenPath.toRealPath(); // Resolves symlinks
env.put(ROOT_PATH_PARAM, rootPath);
```

### 4. Handle Multiple Filesystems

```java
// Keep track of which filesystem paths belong to
Path path1 = fs1.getPath("/file");
Path path2 = fs2.getPath("/file");
assertNotEquals(path1, path2); // Different filesystems!
```

## Running the Demo

```bash
# Compile
javac *.java

# Run
java PathDelegatingDemo
```

Output shows:
1. Delegating to file-based path
2. Delegating to memory-based path  
3. Nested delegation (delegate of delegate)

## Integration

### With Existing Code

```java
// Old code
public void processFiles(Path root) {
    // Processes files in root and subdirectories
}

// New code - add sandboxing
Path unsafeRoot = Paths.get(userInput);
FileSystem sandbox = createDelegate(unsafeRoot);
processFiles(sandbox.getPath("/")); // Safe!
```

### With Testing Frameworks

```java
@BeforeEach
void setUp() {
    memFs = createMemoryFS();
    Path testRoot = memFs.getPath("/test-" + testName);
    Files.createDirectories(testRoot);
    
    testFS = createDelegate(testRoot);
}

@AfterEach
void tearDown() {
    testFS.close();
    memFs.close();
}
```

## Summary

The Path-delegating filesystem is the most flexible implementation:

- ✅ Works with **any** Path
- ✅ **Composable** (can nest delegates)
- ✅ **Secure** (path validation)
- ✅ **Clean** (NIO.2 only, no File dependency)
- ✅ **Versatile** (testing, sandboxing, isolation)

Use it when you need maximum flexibility and don't want to be tied to a specific filesystem implementation.
