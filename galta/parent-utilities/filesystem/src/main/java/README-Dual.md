# Dual FileSystem Implementation with Shared Base Classes

This project demonstrates two complete Java NIO.2 FileSystem implementations that share common base classes to maximize code reuse:

1. **File-based FileSystem** - Delegates to `java.io.File` API
2. **Memory-based FileSystem** - Stores everything in memory

## Architecture Overview

```
Abstract Base Classes (Shared)
├── AbstractFileSystemProvider
│   ├── Common provider logic
│   ├── URI handling
│   └── Default attribute implementations
├── AbstractFileSystem
│   ├── Common filesystem operations
│   ├── Path creation
│   └── PathMatcher implementations
└── AbstractPath
    ├── Path manipulation (resolve, relativize, normalize)
    ├── Path comparison
    └── Iterator support

File-based Implementation          Memory-based Implementation
├── FileFileSystemProvider         ├── MemoryFileSystemProvider
│   └── Delegates to File API      │   └── In-memory operations
├── FileFileSystem                 ├── MemoryFileSystem
│   └── Uses File roots            │   └── Virtual root + node map
├── FilePath                       ├── MemoryPath
│   └── Wraps java.io.File         │   └── Pure string paths
└── FileBasedFileStore             ├── MemoryFileStore
    └── Delegates to File          │   └── Tracks memory usage
                                   └── MemoryFileNode
                                       └── In-memory file/dir representation
```

## Shared Components

### AbstractFileSystemProvider
**Location:** Common base class for all providers

**Shared Functionality:**
- URI validation and scheme checking
- FileSystem lifecycle management (creation, retrieval, removal)
- Path creation from URIs
- Attribute reading/writing default implementations
- `isSameFile()` implementation
- Path validation and type checking

**What Subclasses Override:**
- `createFileSystem()` - Create specific FileSystem instance
- `createPath()` - Create specific Path instance
- `newByteChannel()` - Implementation-specific I/O
- `newDirectoryStream()` - Implementation-specific directory listing
- File operations (create, delete, copy, move)
- Access checking

### AbstractFileSystem
**Location:** Common base class for all filesystems

**Shared Functionality:**
- Lifecycle management (open/close)
- Path creation from strings
- PathMatcher implementations (glob and regex)
- Separator handling
- FileStore and root enumeration interface
- Attribute view support

**What Subclasses Override:**
- `createPath()` - Return specific Path implementation
- `getRootDirectories()` - Platform or implementation-specific roots
- `getFileStores()` - Platform or implementation-specific stores

### AbstractPath
**Location:** Common base class for all paths

**Shared Functionality:**
- Path parsing and component extraction
- `resolve()`, `relativize()`, `normalize()`
- `startsWith()`, `endsWith()`
- `getParent()`, `getFileName()`, `getRoot()`
- `subpath()`, `getName()`, `getNameCount()`
- Path comparison and equality
- Iterator implementation
- All path manipulation logic (~400 lines)

**What Subclasses Override:**
- `toUri()` - Implementation-specific URI format
- `toAbsolutePath()` - Implementation-specific absolute resolution
- `toRealPath()` - Implementation-specific real path resolution
- `toFile()` - Only File-based can convert to File
- `createPath()` - Return specific Path instance

## Code Reuse Statistics

| Component | Lines of Code | Shared % |
|-----------|---------------|----------|
| Path logic | ~400 | 100% |
| PathMatcher | ~80 | 100% |
| FileSystem base | ~150 | 100% |
| Provider base | ~100 | 100% |
| **Total Shared** | **~730** | **~60%** |

## File-based Implementation

### FileFileSystemProvider
- Scheme: `file-impl://`
- Delegates all I/O to `java.io.File`
- Uses `Files.copy()`, `Files.move()` for operations
- Real filesystem attributes

### FileFileSystem
- Uses `File.listRoots()` for root directories
- Creates FilePath instances

### FilePath
- Wraps `java.io.File`
- Can convert to `File` with `toFile()`
- Uses File API for `getCanonicalPath()`

### FileBasedFileStore
- Delegates to `File` for space information
- Reports actual disk space

## Memory-based Implementation

### MemoryFileSystemProvider
- Scheme: `memory://`
- All operations in memory
- Custom `MemoryByteChannel` for I/O
- Simulated file attributes

### MemoryFileSystem
- Single root: `/`
- `ConcurrentHashMap<String, MemoryFileNode>` for storage
- Thread-safe operations
- Tracks total space usage

### MemoryPath
- Pure string-based paths
- Cannot convert to `File`
- Fast path operations

### MemoryFileNode
- Represents file or directory
- Stores:
  - Content (byte array for files)
  - Children (Map for directories)
  - Attributes (timestamps, permissions)
- Thread-safe

### MemoryFileStore
- Virtual 1 GB capacity
- Tracks actual memory usage
- Node count statistics

## Key Design Patterns

### Template Method Pattern
Base classes define the algorithm structure, subclasses fill in specifics:
```java
// In AbstractFileSystemProvider
public Path getPath(URI uri) {
    checkUri(uri);  // Common validation
    String path = uri.getPath();
    return createPath(getFileSystem(uri), path);  // Subclass-specific
}
```

### Strategy Pattern
Different I/O strategies for File vs Memory:
```java
// FileFileSystemProvider
return Files.newByteChannel(file.toPath(), ...);  // Delegate to File

// MemoryFileSystemProvider
return new MemoryByteChannel(node, ...);  // Custom in-memory channel
```

### Factory Method Pattern
Each filesystem creates its own Path type:
```java
protected abstract AbstractPath createPath(String path);
```

## Usage Examples

### File-based FileSystem
```java
FileFileSystemProvider provider = new FileFileSystemProvider();
URI uri = URI.create("file-impl:///");

try (FileSystem fs = provider.newFileSystem(uri, new HashMap<>())) {
    Path path = fs.getPath("/tmp/test.txt");
    Files.write(path, "Hello World".getBytes());
    
    // Delegates to java.io.File
    File file = ((FilePath) path).toFile();
    System.out.println("Real file: " + file.exists());
}
```

### Memory-based FileSystem
```java
MemoryFileSystemProvider provider = new MemoryFileSystemProvider();
URI uri = URI.create("memory:///");

try (FileSystem fs = provider.newFileSystem(uri, new HashMap<>())) {
    Path path = fs.getPath("/test.txt");
    Files.write(path, "Hello World".getBytes());
    
    // Everything in memory
    MemoryFileSystem memFs = (MemoryFileSystem) fs;
    System.out.println("Used space: " + memFs.getTotalUsedSpace());
}
```

### Using Both with Polymorphism
```java
public void processFiles(AbstractFileSystemProvider provider) {
    URI uri = URI.create(provider.getScheme() + ":///");
    
    try (FileSystem fs = provider.newFileSystem(uri, new HashMap<>())) {
        Path path = fs.getPath("/test.txt");
        Files.write(path, "Data".getBytes());
        
        // Same code works for both implementations!
        System.out.println("Size: " + Files.size(path));
    }
}

// Use with either implementation
processFiles(new FileFileSystemProvider());
processFiles(new MemoryFileSystemProvider());
```

## Running the Demo

Compile:
```bash
javac *.java
```

Run the comparison demo:
```bash
java DualFileSystemDemo
```

The demo will:
1. Create a File-based filesystem
2. Perform various operations
3. Create a Memory-based filesystem
4. Perform the same operations
5. Show that both behave identically despite different implementations

## Benefits of Shared Base Classes

1. **Code Reuse**: ~60% of code is shared
2. **Consistency**: Both implementations behave the same way
3. **Maintainability**: Fix a bug in the base class, both implementations benefit
4. **Testability**: Write tests once for AbstractFileSystem
5. **Extensibility**: Easy to add new implementations
6. **Type Safety**: Compile-time checking via abstract classes

## Extending the Framework

To add a new FileSystem implementation:

1. Extend `AbstractFileSystemProvider`
2. Extend `AbstractFileSystem`
3. Extend `AbstractPath`
4. Implement your specific I/O logic
5. Create a FileStore implementation

Example: ZipFileSystem, S3FileSystem, FTPFileSystem, etc.

## Comparison Table

| Feature | File-based | Memory-based | Shared Code |
|---------|------------|--------------|-------------|
| Path manipulation | ✅ | ✅ | Yes (Abstract) |
| PathMatcher | ✅ | ✅ | Yes (Abstract) |
| Read/Write | Delegates to File | In-memory buffer | No |
| Directory listing | File.listFiles() | Map iteration | No |
| Persistence | Yes | No | N/A |
| Speed | Disk I/O | RAM | N/A |
| Capacity | Disk size | ~1 GB virtual | N/A |
| Thread-safety | File system | ConcurrentHashMap | Partial |

## Limitations

### File-based:
- Disk I/O overhead
- Platform-dependent paths
- Requires file permissions

### Memory-based:
- Limited to available RAM
- Data lost on JVM shutdown
- No symbolic link support
- 1 GB virtual limit

## Advanced Features

### Both Support:
- ✅ Basic file operations (CRUD)
- ✅ Directory traversal
- ✅ Path normalization
- ✅ Path matchers (glob/regex)
- ✅ File attributes (basic)
- ✅ Copy/Move operations
- ✅ Access control checking

### Neither Supports:
- ❌ WatchService
- ❌ UserPrincipalLookupService
- ❌ Symbolic links
- ❌ Extended attributes
- ❌ File locking

## Thread Safety

- **File-based**: Relies on OS filesystem thread-safety
- **Memory-based**: Uses `ConcurrentHashMap` for node storage
- **Base classes**: Thread-safe for read operations

## Performance Considerations

**File-based:**
- Good for large files
- Persistent storage
- Disk I/O bottleneck

**Memory-based:**
- Excellent for small/temp files
- Very fast access
- Memory usage grows with files
- Ideal for testing

## License

Example code for educational purposes.
