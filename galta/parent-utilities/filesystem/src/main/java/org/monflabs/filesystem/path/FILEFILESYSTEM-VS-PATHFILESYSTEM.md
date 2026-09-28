# FileFileSystem vs PathFileSystem - Comparison Guide

## Quick Comparison

| Feature | FileFileSystem | PathFileSystem |
|---------|----------------|----------------|
| **API** | `java.io.File` (legacy) | `java.nio.file.Path` (modern) |
| **Separator** | OS-dependent (`File.separator`) | Always `/` |
| **Root Type** | `File` | `Path` |
| **Cross-Platform** | ❌ Paths differ by OS | ✅ Same paths everywhere |
| **Translation** | None | Automatic `/` ↔ OS separator |
| **Modern** | ❌ Legacy API | ✅ NIO.2 API |

## Side-by-Side Examples

### Creating Filesystem

**FileFileSystem:**
```java
File root = new File("/home/user/data");
FileFileSystemProvider provider = new FileFileSystemProvider();

Map<String, Object> env = new HashMap<>();
env.put(FileFileSystemProvider.ROOT_PARAM, root);

FileSystem fs = provider.newFileSystem(URI.create("file:///"), env);

// Separator is OS-dependent
System.out.println(fs.getSeparator());  // "/" on Unix, "\\" on Windows
```

**PathFileSystem:**
```java
Path rootPath = Paths.get("/home/user/data");
PathFileSystemProvider provider = new PathFileSystemProvider();

Map<String, Object> env = new HashMap<>();
env.put(PathFileSystemProvider.ROOT_PATH_PARAM, rootPath);

FileSystem fs = provider.newFileSystem(URI.create("pathfs:///"), env);

// Separator is always /
System.out.println(fs.getSeparator());  // Always "/"
```

### Creating Files

**FileFileSystem (Unix):**
```java
Path file = fs.getPath("/docs/file.txt");
Files.write(file, data);
// Path: "/docs/file.txt"
```

**FileFileSystem (Windows):**
```java
Path file = fs.getPath("\\docs\\file.txt");  // Must use \\
Files.write(file, data);
// Path: "\docs\file.txt"
```

**PathFileSystem (All OS):**
```java
Path file = fs.getPath("/docs/file.txt");  // Always use /
Files.write(file, data);
// Path: "/docs/file.txt" (same everywhere!)
```

### Path Components

**FileFileSystem:**
```java
// On Unix
Path p = fs.getPath("/dir/file.txt");
p.getParent();     // "/dir"
p.toString();      // "/dir/file.txt"

// On Windows
Path p = fs.getPath("\\dir\\file.txt");
p.getParent();     // "\dir"
p.toString();      // "\dir\file.txt"
```

**PathFileSystem:**
```java
// On all platforms
Path p = fs.getPath("/dir/file.txt");
p.getParent();     // "/dir" (same everywhere!)
p.toString();      // "/dir/file.txt" (same everywhere!)
```

## When to Use Each

### Use FileFileSystem When:

1. **Legacy compatibility** - Working with existing `File`-based code
2. **Simple delegation** - Just want to wrap disk operations
3. **No cross-platform needs** - Only targeting one OS
4. **Minimal abstraction** - Want direct OS path handling

### Use PathFileSystem When:

1. **Cross-platform code** - Same code runs on Windows/Unix/Mac
2. **Modern NIO.2** - Using `Path` API throughout
3. **Path normalization** - Want consistent "/" separator
4. **Testing** - Need predictable path behavior
5. **Configuration files** - Store portable "/" paths

## Code Migration

### From FileFileSystem to PathFileSystem

**Step 1: Change Root Type**
```java
// Before
File root = new File("/data");

// After
Path rootPath = Paths.get("/data");
```

**Step 2: Change Provider**
```java
// Before
FileFileSystemProvider provider = new FileFileSystemProvider();

// After
PathFileSystemProvider provider = new PathFileSystemProvider();
```

**Step 3: Change URI Scheme**
```java
// Before
URI.create("file:///myfs")

// After
URI.create("pathfs:///myfs")
```

**Step 4: Update Path Construction (Windows)**
```java
// Before (Windows)
Path file = fs.getPath("\\docs\\file.txt");

// After (All OS)
Path file = fs.getPath("/docs/file.txt");  // Use / everywhere!
```

## Real-World Example

### Scenario: Configuration File Reader

**With FileFileSystem:**
```java
public class ConfigReader {
    private final FileSystem fs;
    
    public ConfigReader(File configRoot) throws IOException {
        Map<String, Object> env = new HashMap<>();
        env.put("root", configRoot);
        
        FileFileSystemProvider provider = new FileFileSystemProvider();
        this.fs = provider.newFileSystem(URI.create("file:///"), env);
    }
    
    public String readConfig(String name) throws IOException {
        // Problem: Path separator depends on OS
        String sep = fs.getSeparator();
        Path path = fs.getPath(sep + "config" + sep + name + ".conf");
        return Files.readString(path);
    }
}
```

**With PathFileSystem:**
```java
public class ConfigReader {
    private final FileSystem fs;
    
    public ConfigReader(Path configRoot) throws IOException {
        Map<String, Object> env = new HashMap<>();
        env.put("rootPath", configRoot);
        
        PathFileSystemProvider provider = new PathFileSystemProvider();
        this.fs = provider.newFileSystem(URI.create("pathfs:///"), env);
    }
    
    public String readConfig(String name) throws IOException {
        // Clean: Always use / separator
        Path path = fs.getPath("/config/" + name + ".conf");
        return Files.readString(path);
    }
}
```

## Performance

Both filesystems have similar performance since they delegate to OS:

| Operation | FileFileSystem | PathFileSystem |
|-----------|----------------|----------------|
| **File read** | Direct | Direct (+path translation) |
| **File write** | Direct | Direct (+path translation) |
| **Directory list** | Direct | Direct (+path translation) |
| **Path translation** | None | ~1μs per operation |

**Overhead:** PathFileSystem adds ~1 microsecond per operation for path translation, which is negligible compared to actual I/O.

## API Differences

### FileFileSystem API

```java
// Uses File internally
public class FileFileSystem extends AbstractFileSystem {
    private final File root;
    
    public File getRoot();
    public File toFile(Path path);
}
```

### PathFileSystem API

```java
// Uses Path internally
public class PathFileSystem extends AbstractFileSystem {
    private final java.nio.file.Path rootPath;
    
    public java.nio.file.Path getRootPath();
    public java.nio.file.Path toOSPath(String virtualPath);
    public String toVirtualPath(java.nio.file.Path osPath);
}
```

## Testing Differences

### FileFileSystem Testing

```java
@Test
void testFileOperations() throws IOException {
    File tempDir = createTempDirectory();
    FileSystem fs = createFileFS(tempDir);
    
    // Must use OS-specific separator
    String sep = fs.getSeparator();
    Path file = fs.getPath(sep + "test.txt");
    
    // Different assertions for different OS
    if (sep.equals("/")) {
        assertEquals("/test.txt", file.toString());
    } else {
        assertEquals("\\test.txt", file.toString());
    }
}
```

### PathFileSystem Testing

```java
@Test
void testFileOperations() throws IOException {
    Path tempDir = createTempDirectory();
    FileSystem fs = createPathFS(tempDir);
    
    // Always use / separator
    Path file = fs.getPath("/test.txt");
    
    // Same assertion on all OS
    assertEquals("/test.txt", file.toString());
}
```

## Summary

### FileFileSystem: Legacy-Compatible

**Pros:**
- ✅ Works with existing `File` code
- ✅ Direct OS path handling
- ✅ Simple implementation

**Cons:**
- ❌ OS-dependent paths
- ❌ Legacy `File` API
- ❌ Different code for different OS

### PathFileSystem: Modern & Cross-Platform

**Pros:**
- ✅ Modern NIO.2 `Path` API
- ✅ Cross-platform path normalization
- ✅ Always uses "/" separator
- ✅ Same code on all OS
- ✅ Better for testing

**Cons:**
- ⚠️ Tiny overhead for path translation
- ⚠️ Cannot use "\\" separator

## Recommendation

**Use PathFileSystem for new projects:**
- ✅ Modern API
- ✅ Cross-platform
- ✅ Easier testing
- ✅ Cleaner code

**Use FileFileSystem only if:**
- You need legacy `File` compatibility
- You're maintaining existing code

PathFileSystem is the better choice for modern Java applications! 🚀
