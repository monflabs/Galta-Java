# Architecture Visualization

## Class Hierarchy

```
┌─────────────────────────────────────────────────────────────┐
│                    Java NIO.2 Interfaces                     │
│  FileSystemProvider, FileSystem, Path, FileStore            │
└─────────────────────────────────────────────────────────────┘
                              ▲
                              │
                              │ implements
                              │
┌─────────────────────────────────────────────────────────────┐
│                    Abstract Base Classes                     │
│              (Shared code ~60% of total)                    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌────────────────────────────────────────────────┐        │
│  │    AbstractFileSystemProvider                   │        │
│  │  • URI validation                              │        │
│  │  • FileSystem lifecycle management             │        │
│  │  • Default attribute implementations           │        │
│  │  • Path validation                             │        │
│  └────────────────────────────────────────────────┘        │
│                       ▲                                      │
│                       │ extends                              │
│         ┌─────────────┴─────────────┐                       │
│         │                           │                       │
│  ┌──────────────┐          ┌──────────────┐               │
│  │   Abstract   │          │   Abstract   │               │
│  │  FileSystem  │          │     Path     │               │
│  │              │          │              │               │
│  │ • Lifecycle  │          │ • resolve()  │               │
│  │ • Separator  │          │ • normalize()│               │
│  │ • PathMatcher│          │ • relativize()│              │
│  └──────────────┘          └──────────────┘               │
│                                                              │
└─────────────────────────────────────────────────────────────┘
                              ▲
                              │ extends
                              │
        ┌─────────────────────┴─────────────────────┐
        │                                           │
┌───────────────────────┐              ┌───────────────────────┐
│  File-based Impl      │              │  Memory-based Impl    │
│  (delegates to File)  │              │  (in-memory storage)  │
├───────────────────────┤              ├───────────────────────┤
│                       │              │                       │
│ FileFileSystemProvider│              │ MemoryFileSystemProvider│
│  └─> Uses File I/O    │              │  └─> MemoryByteChannel│
│                       │              │                       │
│ FileFileSystem        │              │ MemoryFileSystem      │
│  └─> File.listRoots() │              │  └─> Node Map         │
│                       │              │                       │
│ FilePath              │              │ MemoryPath            │
│  └─> Wraps File       │              │  └─> Pure string      │
│  └─> toFile() works   │              │  └─> toFile() throws  │
│                       │              │                       │
│ FileBasedFileStore    │              │ MemoryFileStore       │
│  └─> Real disk space  │              │  └─> Virtual 1GB      │
│                       │              │                       │
│                       │              │ MemoryFileNode        │
│                       │              │  └─> File/Dir data    │
└───────────────────────┘              └───────────────────────┘
```

## Data Flow Example: Writing a File

### File-based FileSystem
```
User Code
   │
   ├─> Files.write(path, bytes)
   │
   ▼
FileFileSystemProvider.newByteChannel()
   │
   ├─> FilePath.toFile() → java.io.File
   │
   ▼
Files.newByteChannel(file.toPath(), ...)
   │
   ▼
Operating System / Disk I/O
```

### Memory-based FileSystem
```
User Code
   │
   ├─> Files.write(path, bytes)
   │
   ▼
MemoryFileSystemProvider.newByteChannel()
   │
   ├─> MemoryFileSystem.getNode(path)
   │   └─> ConcurrentHashMap lookup
   │
   ▼
MemoryByteChannel (custom implementation)
   │
   ├─> MemoryFileNode.setContent(bytes)
   │
   ▼
byte[] array in RAM
```

## Shared vs Specific Code Distribution

```
Total Implementation: ~2000 lines

┌────────────────────────────────────────┐
│ AbstractPath (~400 lines)              │ ◄── 100% shared
│  • resolve, relativize, normalize      │
│  • getParent, getFileName, etc.        │
└────────────────────────────────────────┘

┌────────────────────────────────────────┐
│ AbstractFileSystem (~150 lines)        │ ◄── 100% shared
│  • PathMatcher (glob/regex)            │
│  • Lifecycle management                │
└────────────────────────────────────────┘

┌────────────────────────────────────────┐
│ AbstractFileSystemProvider (~180 lines)│ ◄── 100% shared
│  • URI validation                       │
│  • Default attribute handling          │
└────────────────────────────────────────┘

┌────────────────────────────────────────┐
│ File-based Implementation (~400 lines) │ ◄── File-specific
│  • Delegates to java.io.File           │
└────────────────────────────────────────┘

┌────────────────────────────────────────┐
│ Memory-based Implementation (~870 lines)│ ◄── Memory-specific
│  • MemoryFileNode                       │
│  • MemoryByteChannel                   │
│  • ConcurrentHashMap storage           │
└────────────────────────────────────────┘

Shared: ~730 lines (60%)
Total Specific: ~1270 lines (40%)
```

## Method Delegation Example

### resolve() - 100% Shared in AbstractPath
```java
// This method is identical for both implementations
@Override
public Path resolve(Path other) {
    if (other.isAbsolute()) {
        return other;
    }
    if (other.toString().isEmpty()) {
        return this;
    }
    String otherPath = other.toString();
    if (path.isEmpty()) {
        return createPath(otherPath);
    }
    if (path.endsWith(separator)) {
        return createPath(path + otherPath);
    }
    return createPath(path + separator + otherPath);
}
```

### newByteChannel() - Implementation Specific

**File-based:**
```java
@Override
public SeekableByteChannel newByteChannel(Path path, ...) {
    File file = ((FilePath) path).toFile();
    return Files.newByteChannel(file.toPath(), options, attrs);
    // ↑ Delegates to existing File API
}
```

**Memory-based:**
```java
@Override
public SeekableByteChannel newByteChannel(Path path, ...) {
    MemoryFileNode node = fs.getNode((MemoryPath) path);
    return new MemoryByteChannel(node, read, write, append);
    // ↑ Custom in-memory implementation
}
```

## Polymorphism in Action

```java
// Same code works with both implementations!
public void processFiles(AbstractFileSystemProvider provider) {
    URI uri = URI.create(provider.getScheme() + ":///");
    
    try (FileSystem fs = provider.newFileSystem(uri, new HashMap<>())) {
        Path file = fs.getPath("/test.txt");
        
        // Write
        Files.write(file, "Hello World".getBytes());
        
        // Read
        String content = new String(Files.readAllBytes(file));
        
        // Copy
        Path copy = fs.getPath("/copy.txt");
        Files.copy(file, copy);
        
        // List directory
        Path dir = fs.getPath("/");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            stream.forEach(System.out::println);
        }
    }
}

// Use with File-based implementation
processFiles(new FileFileSystemProvider());
// Scheme: file-impl:///
// Storage: Actual disk files

// Use with Memory-based implementation
processFiles(new MemoryFileSystemProvider());
// Scheme: memory:///
// Storage: In-memory HashMap
```

## Design Patterns Applied

### 1. Template Method Pattern
```
AbstractFileSystemProvider.getPath(URI)
├─ checkUri(uri)           ← Template method (common)
├─ extract path from URI   ← Template method (common)
└─ createPath(fs, path)    ← Hook method (specific)
```

### 2. Factory Method Pattern
```
AbstractFileSystem
├─ getPath(String...)
│   └─ calls createPath() ← Factory method
│
FileFileSystem              MemoryFileSystem
└─ createPath()             └─ createPath()
    returns FilePath            returns MemoryPath
```

### 3. Strategy Pattern
```
I/O Strategy:
├─ File-based:  Delegate to java.io.File
└─ Memory-based: Custom MemoryByteChannel

Storage Strategy:
├─ File-based:  OS filesystem
└─ Memory-based: ConcurrentHashMap<String, MemoryFileNode>
```

### 4. Bridge Pattern
```
Abstraction                 Implementor
AbstractPath    ◄────────►  AbstractFileSystem
├─ FilePath                 ├─ FileFileSystem (File API)
└─ MemoryPath               └─ MemoryFileSystem (Memory)
```

## Extension Example: Adding a ZipFileSystem

To add a new implementation:

```java
// 1. Extend the provider
public class ZipFileSystemProvider extends AbstractFileSystemProvider {
    @Override
    public String getScheme() { return "zip"; }
    
    @Override
    protected AbstractFileSystem createFileSystem(URI uri, Map<String, ?> env) {
        return new ZipFileSystem(this, uri);
    }
    
    // Implement zip-specific I/O operations
}

// 2. Extend the filesystem
public class ZipFileSystem extends AbstractFileSystem {
    private ZipFile zipFile;
    
    // Implement zip-specific methods
}

// 3. Extend the path
public class ZipPath extends AbstractPath {
    // Zip-specific path handling
}

// 4. Get all the shared functionality for free!
//    - Path manipulation (resolve, relativize, normalize)
//    - PathMatcher (glob, regex)
//    - Lifecycle management
//    - etc.
```

All the shared code (~730 lines) is immediately available!
