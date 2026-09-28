# ZipFileSystem - Read-Only ZIP File Access

## Overview

`ZipFileSystem` is a read-only filesystem implementation that exposes the contents of a ZIP file as a navigable filesystem using the Java NIO.2 API.

**Key Features:**
- ✅ Read-only access to ZIP contents
- ✅ Standard NIO.2 Path API
- ✅ Directory navigation and listing
- ✅ File reading with SeekableByteChannel
- ✅ Pattern matching (glob/regex)
- ✅ Handles implicit directories (directories not explicitly stored in ZIP)
- ✅ Full attribute support

## Architecture

```
ZipFileSystemProvider          - FileSystemProvider implementation
    └─> ZipFileSystem           - FileSystem backed by java.util.zip.ZipFile
        └─> ZipPath             - Path within ZIP
            └─> ZipEntry        - Actual ZIP entry (file or directory)
```

### Design Decisions

1. **Read-Only**: ZIP filesystem is strictly read-only to prevent corruption
2. **Reuses Abstract Base**: Extends `AbstractFileSystem`, `AbstractPath`, etc.
3. **Handles Implicit Directories**: Automatically detects directories that aren't explicitly stored
4. **Memory-Based Reading**: Files are read entirely into memory (standard for ZIP access)

## Usage

### Basic Usage

```java
// Open a ZIP file as a filesystem
Path zipFile = Paths.get("archive.zip");

ZipFileSystemProvider provider = new ZipFileSystemProvider();
Map<String, Object> env = new HashMap<>();
env.put(ZipFileSystemProvider.ZIP_FILE_PARAM, zipFile);

URI uri = URI.create("zip:///myarchive");

try (FileSystem fs = provider.newFileSystem(uri, env)) {
    // Access files in the ZIP
    Path readme = fs.getPath("/README.md");
    String content = new String(Files.readAllBytes(readme));
    System.out.println(content);
    
    // List directory
    Path docs = fs.getPath("/docs");
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(docs)) {
        for (Path entry : stream) {
            System.out.println(entry.getFileName());
        }
    }
}
```

### Navigate Directory Structure

```java
try (FileSystem fs = provider.newFileSystem(uri, env)) {
    Path root = fs.getPath("/");
    
    // Walk entire tree
    Files.walk(root)
        .filter(Files::isRegularFile)
        .forEach(path -> {
            try {
                long size = Files.size(path);
                System.out.println(path + " (" + size + " bytes)");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
}
```

### Find Files by Pattern

```java
try (FileSystem fs = provider.newFileSystem(uri, env)) {
    // Find all .java files
    PathMatcher matcher = fs.getPathMatcher("glob:**/*.java");
    
    Files.walk(fs.getPath("/"))
        .filter(matcher::matches)
        .forEach(System.out::println);
}
```

### Read File Attributes

```java
try (FileSystem fs = provider.newFileSystem(uri, env)) {
    Path file = fs.getPath("/data.txt");
    
    BasicFileAttributes attrs = Files.readAttributes(
        file, BasicFileAttributes.class);
    
    System.out.println("Size: " + attrs.size());
    System.out.println("Modified: " + attrs.lastModifiedTime());
    System.out.println("Is File: " + attrs.isRegularFile());
}
```

## Components

### ZipFileSystemProvider

**Scheme**: `zip://`

**Required Parameters**:
- `zipFile` (Path or String): Path to the ZIP file to open

**Operations**:
- ✅ `newByteChannel()` - Open files for reading
- ✅ `newDirectoryStream()` - List directory contents
- ✅ `readAttributes()` - Get file attributes
- ✅ `checkAccess()` - Verify read access
- ❌ `createDirectory()` - Throws `ReadOnlyFileSystemException`
- ❌ `delete()` - Throws `ReadOnlyFileSystemException`
- ❌ `copy()` - Within ZIP throws exception (to external FS allowed)
- ❌ `move()` - Throws `ReadOnlyFileSystemException`
- ❌ `setAttribute()` - Throws `ReadOnlyFileSystemException`

### ZipFileSystem

**Properties**:
- `isReadOnly()` → always returns `true`
- `getSeparator()` → returns `/`
- Root directory → `/`

**Key Methods**:
- `getPath()` - Create path within ZIP
- `getPathMatcher()` - Create glob/regex matcher
- `getEntry()` - Internal: Get ZipEntry for path
- `listDirectory()` - Internal: List directory contents
- `readEntry()` - Internal: Read entry bytes

**Implicit Directory Handling**:

ZIP files may not explicitly store directory entries. For example:
```
archive.zip contains:
  src/main/App.java
  src/test/AppTest.java
```

The directories `src/`, `src/main/`, and `src/test/` are implicit. ZipFileSystem automatically detects these by scanning for entries with common prefixes.

### ZipPath

**Represents**: A path within the ZIP file

**Key Features**:
- Implements standard Path interface
- Cannot be converted to `File` (throws `UnsupportedOperationException`)
- `toUri()` returns ZIP-specific URI: `zip://file.zip!/path/to/entry`

**Path Operations**:
```java
Path path = fs.getPath("/src/main/App.java");

path.getFileName()   → "App.java"
path.getParent()     → "/src/main"
path.getRoot()       → "/"
path.getNameCount()  → 3
path.getName(0)      → "src"
path.getName(1)      → "main"
path.getName(2)      → "App.java"
```

## Read-Only Enforcement

All write operations throw `ReadOnlyFileSystemException`:

```java
try (FileSystem fs = provider.newFileSystem(uri, env)) {
    Path file = fs.getPath("/new.txt");
    
    // All of these throw ReadOnlyFileSystemException:
    Files.write(file, "data".getBytes());           // ❌
    Files.delete(file);                             // ❌
    Files.createDirectory(fs.getPath("/newdir"));   // ❌
    Files.move(file, fs.getPath("/other.txt"));     // ❌
    
    // Reading is allowed:
    byte[] content = Files.readAllBytes(file);      // ✅
}
```

## Use Cases

### 1. Application Resources

```java
// Package resources in a ZIP
Path resources = Paths.get("app-resources.zip");

try (FileSystem fs = openZip(resources)) {
    // Load config
    Path config = fs.getPath("/config/app.properties");
    Properties props = new Properties();
    props.load(Files.newInputStream(config));
    
    // Load templates
    Path template = fs.getPath("/templates/email.html");
    String html = new String(Files.readAllBytes(template));
}
```

### 2. Archive Inspection

```java
// Inspect archive contents
try (FileSystem fs = openZip(Paths.get("backup.zip"))) {
    // Get total size
    long totalSize = Files.walk(fs.getPath("/"))
        .filter(Files::isRegularFile)
        .mapToLong(p -> {
            try { return Files.size(p); }
            catch (IOException e) { return 0; }
        })
        .sum();
    
    System.out.println("Total size: " + totalSize + " bytes");
    
    // Count file types
    Map<String, Long> fileTypes = Files.walk(fs.getPath("/"))
        .filter(Files::isRegularFile)
        .collect(Collectors.groupingBy(
            p -> getExtension(p.toString()),
            Collectors.counting()
        ));
}
```

### 3. Data Distribution

```java
// Distribute read-only data sets
Path dataZip = Paths.get("dataset-2024.zip");

try (FileSystem fs = openZip(dataZip)) {
    // Read CSV data
    Path data = fs.getPath("/data/records.csv");
    List<String> lines = Files.readAllLines(data);
    
    // Process data
    lines.stream()
        .skip(1)  // Skip header
        .map(line -> parseRecord(line))
        .forEach(this::processRecord);
}
```

### 4. Testing

```java
// Create test fixtures in ZIP
@BeforeAll
static void setupTestData() throws IOException {
    Path testZip = createTestZip();
    
    try (FileSystem fs = openZip(testZip)) {
        // Verify test data structure
        assertTrue(Files.exists(fs.getPath("/test-data/input.json")));
        assertTrue(Files.exists(fs.getPath("/test-data/expected.json")));
    }
}

@Test
void testProcessing() throws IOException {
    try (FileSystem fs = openZip(testDataZip)) {
        String input = new String(Files.readAllBytes(
            fs.getPath("/test-data/input.json")));
        String expected = new String(Files.readAllBytes(
            fs.getPath("/test-data/expected.json")));
        
        String result = processData(input);
        assertEquals(expected, result);
    }
}
```

### 5. Extract to Another Filesystem

```java
// Copy from ZIP to memory or disk
try (FileSystem zipFs = openZip(Paths.get("archive.zip"));
     FileSystem memFs = createMemoryFilesystem()) {
    
    // Extract all files to memory filesystem
    Files.walk(zipFs.getPath("/"))
        .filter(Files::isRegularFile)
        .forEach(source -> {
            try {
                Path target = memFs.getPath(source.toString());
                Files.createDirectories(target.getParent());
                Files.copy(source, target);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
}
```

## Performance Characteristics

### Reading
- **Small files (<1 MB)**: Very fast (read into memory)
- **Large files (>10 MB)**: Memory intensive (entire file loaded)
- **Random access**: Fast after initial load
- **Sequential reads**: Efficient

### Directory Operations
- **List directory**: Fast (scans ZIP central directory)
- **Walk tree**: Linear with number of entries
- **Pattern matching**: Linear scan of all entries

### Memory Usage
- **Per-file**: Size of uncompressed file content
- **Metadata**: Minimal (ZipEntry objects)
- **Directory cache**: Not cached (computed on demand)

**Recommendation**: For very large files (>100 MB), consider using standard `ZipInputStream` instead.

## Limitations

### 1. No Write Support
```java
// These operations are not supported:
Files.write(path, data);            // ❌
Files.delete(path);                 // ❌
Files.createDirectory(path);        // ❌
Files.setAttribute(path, attr, val);// ❌
```

### 2. No File Conversion
```java
Path zipPath = zipFs.getPath("/file.txt");
File file = zipPath.toFile();  // ❌ UnsupportedOperationException
```

### 3. No Watch Service
```java
WatchService watcher = zipFs.newWatchService();  // ❌ Not supported
```

### 4. Memory Constraints
- Each file read loads entire content into memory
- Not suitable for very large individual files
- Consider streaming with `ZipInputStream` for huge files

### 5. No ZIP Modification
- Cannot add files to ZIP
- Cannot update existing files
- Cannot delete entries
- ZIP remains unchanged

### 6. Compression Transparency
- All files are automatically decompressed
- No access to compressed size information
- Cannot control compression level

## Comparison with Standard ZIP Filesystem

| Feature | ZipFileSystem | Java's ZipFileSystem (jar:) |
|---------|---------------|------------------------------|
| **Read files** | ✅ | ✅ |
| **Write files** | ❌ | ✅ (if not read-only) |
| **Create ZIP** | ❌ | ✅ |
| **Modify ZIP** | ❌ | ✅ |
| **Scheme** | `zip://` | `jar://` |
| **Base classes** | Custom (AbstractFileSystem) | Standard |
| **Implicit dirs** | ✅ Auto-detected | ✅ |
| **Attributes** | BasicFileAttributes | Full support |

**When to use ZipFileSystem**:
- Need explicit read-only guarantee
- Want consistent API with Memory/File filesystems
- Building custom filesystem stack
- Extending functionality

**When to use standard jar: filesystem**:
- Need to modify ZIPs
- Want full attribute support
- Standard Java is sufficient
- No custom requirements

## Best Practices

### 1. Always Use Try-With-Resources

```java
// Good
try (FileSystem fs = provider.newFileSystem(uri, env)) {
    // Use filesystem
}  // Automatically closed

// Bad
FileSystem fs = provider.newFileSystem(uri, env);
// Use filesystem
fs.close();  // Easy to forget!
```

### 2. Check File Existence

```java
Path file = fs.getPath("/config.xml");

if (Files.exists(file)) {
    processConfig(Files.readAllBytes(file));
} else {
    useDefaultConfig();
}
```

### 3. Handle Large Files Carefully

```java
Path largeFile = fs.getPath("/data/large.bin");

if (Files.size(largeFile) > 100_000_000) {  // > 100 MB
    // Consider alternative approach
    System.err.println("File too large for in-memory loading");
} else {
    byte[] data = Files.readAllBytes(largeFile);
}
```

### 4. Use Pattern Matching for Bulk Operations

```java
PathMatcher matcher = fs.getPathMatcher("glob:**.{jpg,png,gif}");

Files.walk(fs.getPath("/images"))
    .filter(matcher::matches)
    .forEach(this::processImage);
```

### 5. Cache Filesystem Reference

```java
// Good - reuse filesystem
try (FileSystem fs = openZip(zipPath)) {
    processFile(fs.getPath("/file1.txt"));
    processFile(fs.getPath("/file2.txt"));
    processFile(fs.getPath("/file3.txt"));
}

// Bad - opens multiple times
processFile(openZip(zipPath).getPath("/file1.txt"));
processFile(openZip(zipPath).getPath("/file2.txt"));
processFile(openZip(zipPath).getPath("/file3.txt"));
```

## Summary

**ZipFileSystem** provides:
- ✅ Read-only access to ZIP archives
- ✅ Standard NIO.2 Path API
- ✅ Directory navigation and listing
- ✅ Pattern matching
- ✅ Implicit directory handling
- ✅ Consistent with Memory/File filesystems

**Use for**:
- Reading application resources
- Inspecting archives
- Testing with fixture data
- Read-only data distribution
- Archive processing

**Not for**:
- Creating or modifying ZIPs
- Very large individual files (>100 MB)
- Write operations
- Watch service monitoring

ZipFileSystem is the perfect choice when you need read-only access to ZIP contents with a clean, standard filesystem API!
