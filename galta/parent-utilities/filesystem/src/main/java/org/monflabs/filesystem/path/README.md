# PathFileSystem - Usage Guide

## Overview

PathFileSystem is a modern NIO.2-based filesystem implementation that:
- ✅ Uses `java.nio.file.Path` instead of legacy `java.io.File`
- ✅ **Always uses "/" as separator** (even on Windows!)
- ✅ Transparently translates between "/" and OS separator
- ✅ Supports sandboxing to a specific directory
- ✅ Delegates to actual OS filesystem

## Key Features

### 1. Cross-Platform Path Normalization

```java
// On Windows:
FileSystem fs = createPathFS(Paths.get("C:\\data"));
Path file = fs.getPath("/docs/file.txt");

System.out.println(file);                    // "/docs/file.txt" (uses /)
System.out.println(fs.getSeparator());       // "/" (always!)
System.out.println(((PathPath)file).toOSPath()); 
// "C:\data\docs\file.txt" (Windows path internally)
```

### 2. Sandboxing

```java
// Sandbox to specific directory
Path rootPath = Paths.get("/home/user/sandbox");
FileSystem fs = createPathFS(rootPath);

// All operations confined to sandbox
Path file = fs.getPath("/test.txt");
Files.write(file, data);
// Actually writes to: /home/user/sandbox/test.txt
```

### 3. Modern NIO.2 API

Uses `Path` throughout - no legacy `File` objects!

## Quick Start

### Option 1: Basic Usage

```java
import java.nio.file.*;
import java.util.*;

public class PathFSExample {
    public static void main(String[] args) throws Exception {
        // Create filesystem (no sandboxing)
        PathFileSystemProvider provider = new PathFileSystemProvider();
        FileSystem fs = provider.newFileSystem(
            URI.create("pathfs:///myfs"), 
            new HashMap<>()
        );
        
        // Use it - always with "/" separator!
        Path file = fs.getPath("/docs/file.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "Hello from PathFS!");
        
        String content = Files.readString(file);
        System.out.println(content);
        
        fs.close();
    }
}
```

### Option 2: With Sandboxing

```java
import java.nio.file.*;
import java.util.*;

public class SandboxExample {
    public static void main(String[] args) throws Exception {
        // Create sandbox directory
        Path sandboxRoot = Paths.get("./sandbox");
        Files.createDirectories(sandboxRoot);
        
        // Create sandboxed filesystem
        Map<String, Object> env = new HashMap<>();
        env.put("rootPath", sandboxRoot);
        
        PathFileSystemProvider provider = new PathFileSystemProvider();
        FileSystem fs = provider.newFileSystem(
            URI.create("pathfs:///sandbox"), 
            env
        );
        
        // All operations confined to sandbox
        Path file = fs.getPath("/data/test.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "Sandboxed data");
        
        // This actually creates: ./sandbox/data/test.txt
        
        fs.close();
    }
}
```

## Complete Examples

### Example 1: Cross-Platform File Operations

```java
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

public class CrossPlatformExample {
    
    public static void main(String[] args) throws IOException {
        try (FileSystem fs = createPathFS(null)) {
            
            // Demo 1: Create directory structure
            Path docsDir = fs.getPath("/documents/reports");
            Files.createDirectories(docsDir);
            System.out.println("✓ Created: " + docsDir);
            System.out.println("  Separator: " + fs.getSeparator()); // Always "/"
            
            // Demo 2: Create files with "/" paths
            Path readme = fs.getPath("/documents/README.md");
            Files.writeString(readme, "# Documentation\n\nProject files");
            System.out.println("✓ Created: " + readme);
            
            Path report = fs.getPath("/documents/reports/q4.txt");
            Files.writeString(report, "Q4 Financial Report");
            System.out.println("✓ Created: " + report);
            
            // Demo 3: List directory
            System.out.println("\nFiles in /documents:");
            try (DirectoryStream<Path> stream = 
                    Files.newDirectoryStream(fs.getPath("/documents"))) {
                for (Path entry : stream) {
                    System.out.println("  " + entry);
                }
            }
            
            // Demo 4: Path manipulation (always uses /)
            Path file = fs.getPath("/documents/reports/q4.txt");
            System.out.println("\nPath components:");
            System.out.println("  Full path: " + file);
            System.out.println("  Parent: " + file.getParent());
            System.out.println("  File name: " + file.getFileName());
            System.out.println("  Root: " + file.getRoot());
            
            // Demo 5: Show OS translation
            if (file instanceof PathPath) {
                PathPath pp = (PathPath) file;
                System.out.println("\nOS translation:");
                System.out.println("  Virtual path: " + pp);
                System.out.println("  OS path: " + pp.toOSPath());
            }
        }
    }
    
    private static FileSystem createPathFS(Path rootPath) throws IOException {
        Map<String, Object> env = new HashMap<>();
        if (rootPath != null) {
            env.put("rootPath", rootPath);
        }
        
        PathFileSystemProvider provider = new PathFileSystemProvider();
        return provider.newFileSystem(
            URI.create("pathfs:///demo"), 
            env
        );
    }
}
```

**Output on Windows:**
```
✓ Created: /documents/reports
  Separator: /
✓ Created: /documents/README.md
✓ Created: /documents/reports/q4.txt

Files in /documents:
  /documents/README.md
  /documents/reports

Path components:
  Full path: /documents/reports/q4.txt
  Parent: /documents/reports
  File name: q4.txt
  Root: /

OS translation:
  Virtual path: /documents/reports/q4.txt
  OS path: C:\Users\...\documents\reports\q4.txt
```

### Example 2: Sandboxed Application Data

```java
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

public class SandboxedApp {
    
    private final FileSystem appFS;
    
    public SandboxedApp(Path dataDirectory) throws IOException {
        // Create sandboxed filesystem
        Map<String, Object> env = new HashMap<>();
        env.put("rootPath", dataDirectory);
        
        PathFileSystemProvider provider = new PathFileSystemProvider();
        this.appFS = provider.newFileSystem(
            URI.create("pathfs:///app"), 
            env
        );
    }
    
    public void saveConfig(String key, String value) throws IOException {
        Path configFile = appFS.getPath("/config/" + key + ".conf");
        Files.createDirectories(configFile.getParent());
        Files.writeString(configFile, value);
    }
    
    public String loadConfig(String key) throws IOException {
        Path configFile = appFS.getPath("/config/" + key + ".conf");
        if (Files.exists(configFile)) {
            return Files.readString(configFile);
        }
        return null;
    }
    
    public void saveLogs(String logData) throws IOException {
        Path logDir = appFS.getPath("/logs");
        Files.createDirectories(logDir);
        
        String timestamp = String.valueOf(System.currentTimeMillis());
        Path logFile = appFS.getPath("/logs/app-" + timestamp + ".log");
        Files.writeString(logFile, logData);
    }
    
    public List<Path> listLogs() throws IOException {
        List<Path> logs = new ArrayList<>();
        Path logDir = appFS.getPath("/logs");
        
        if (Files.exists(logDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(logDir, "*.log")) {
                stream.forEach(logs::add);
            }
        }
        
        return logs;
    }
    
    public void close() throws IOException {
        appFS.close();
    }
    
    public static void main(String[] args) throws IOException {
        // Create app with sandboxed data directory
        Path dataDir = Paths.get("./app-data");
        Files.createDirectories(dataDir);
        
        SandboxedApp app = new SandboxedApp(dataDir);
        
        // Save configuration
        app.saveConfig("server", "http://localhost:8080");
        app.saveConfig("timeout", "30");
        
        // Save logs
        app.saveLogs("Application started");
        app.saveLogs("Processing request");
        
        // Load configuration
        String server = app.loadConfig("server");
        System.out.println("Server: " + server);
        
        // List logs
        System.out.println("\nLog files:");
        for (Path log : app.listLogs()) {
            System.out.println("  " + log);
        }
        
        app.close();
        
        // All data is in ./app-data directory:
        // ./app-data/config/server.conf
        // ./app-data/config/timeout.conf
        // ./app-data/logs/app-xxxxx.log
    }
}
```

### Example 3: Testing with Temporary Sandbox

```java
import org.junit.jupiter.api.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class FileProcessorTest {
    
    private FileSystem testFS;
    private Path tempDir;
    
    @BeforeEach
    void setUp() throws IOException {
        // Create temporary directory for testing
        tempDir = Files.createTempDirectory("pathfs-test");
        
        // Create sandboxed filesystem for testing
        Map<String, Object> env = new HashMap<>();
        env.put("rootPath", tempDir);
        
        PathFileSystemProvider provider = new PathFileSystemProvider();
        testFS = provider.newFileSystem(
            URI.create("pathfs:///test"), 
            env
        );
    }
    
    @AfterEach
    void tearDown() throws IOException {
        if (testFS != null && testFS.isOpen()) {
            testFS.close();
        }
        
        // Clean up temp directory
        if (tempDir != null) {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // Ignore
                    }
                });
        }
    }
    
    @Test
    void testFileProcessing() throws IOException {
        // Create test file in sandboxed filesystem
        Path input = testFS.getPath("/input.txt");
        Files.writeString(input, "test data");
        
        // Process file
        FileProcessor processor = new FileProcessor(testFS);
        processor.processFile(input);
        
        // Verify output
        Path output = testFS.getPath("/output.txt");
        assertTrue(Files.exists(output));
        assertEquals("PROCESSED: test data", Files.readString(output));
    }
    
    @Test
    void testDirectoryOperations() throws IOException {
        // All paths use "/" even on Windows
        Path dir = testFS.getPath("/data/subdir");
        Files.createDirectories(dir);
        
        Path file = testFS.getPath("/data/subdir/file.txt");
        Files.writeString(file, "content");
        
        // Verify
        assertTrue(Files.exists(file));
        assertTrue(Files.isDirectory(dir));
        assertEquals("/", testFS.getSeparator());
    }
}
```

## Configuration Options

### Environment Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `rootPath` | `Path` or `String` | Root path for sandboxing (optional) |

### Creating Filesystem

#### No Sandbox (Full Access)

```java
Map<String, Object> env = new HashMap<>();
FileSystem fs = provider.newFileSystem(URI.create("pathfs:///fs"), env);
// Access entire filesystem
```

#### With Sandbox

```java
Map<String, Object> env = new HashMap<>();
env.put("rootPath", Paths.get("/home/user/data"));
FileSystem fs = provider.newFileSystem(URI.create("pathfs:///fs"), env);
// Confined to /home/user/data
```

#### String Path

```java
Map<String, Object> env = new HashMap<>();
env.put("rootPath", "/home/user/data");  // String also works
FileSystem fs = provider.newFileSystem(URI.create("pathfs:///fs"), env);
```

## Key Differences from FileFileSystem

| Feature | FileFileSystem | PathFileSystem |
|---------|----------------|----------------|
| **API** | `java.io.File` (legacy) | `java.nio.file.Path` (modern) |
| **Separator** | OS-dependent (`/` or `\`) | Always `/` |
| **Cross-platform** | ❌ Paths differ by OS | ✅ Same paths on all OS |
| **Root type** | `File` | `Path` |
| **Translation** | None | Automatic `/` ↔ OS separator |

## Path Translation

### Virtual → OS Path

```java
// Virtual path (always uses /)
String virtualPath = "/documents/report.pdf";

// Translation depends on OS:
// Unix:    /documents/report.pdf
// Windows: C:\sandbox\documents\report.pdf (if sandboxed to C:\sandbox)
```

### OS → Virtual Path

```java
// OS path
java.nio.file.Path osPath = Paths.get("C:\\data\\file.txt");

// Virtual path (always /)
String virtualPath = pathFS.toVirtualPath(osPath);
// Result: "/data/file.txt" (or relative to sandbox root)
```

## Best Practices

### 1. Always Use "/" in Paths

```java
// DO: Use / separator
Path file = fs.getPath("/documents/file.txt");

// DON'T: Use \ separator
Path file = fs.getPath("\\documents\\file.txt");  // Wrong!
```

### 2. Use Sandboxing for Security

```java
// Good: Sandbox to specific directory
Map<String, Object> env = new HashMap<>();
env.put("rootPath", Paths.get("./app-data"));
FileSystem fs = provider.newFileSystem(uri, env);

// Now all operations are confined to ./app-data
```

### 3. Close Filesystem When Done

```java
// Good: Try-with-resources
try (FileSystem fs = createPathFS(rootPath)) {
    // Use filesystem
}

// Also good: Explicit close
FileSystem fs = createPathFS(rootPath);
try {
    // Use filesystem
} finally {
    fs.close();
}
```

### 4. Use for Testing

```java
// Create temporary sandbox for each test
@BeforeEach
void setUp() {
    tempDir = Files.createTempDirectory("test");
    testFS = createPathFS(tempDir);
}

@AfterEach
void tearDown() {
    testFS.close();
    deleteRecursively(tempDir);
}
```

## Common Use Cases

### 1. Cross-Platform Applications

```java
// Write once, works on all platforms
FileSystem fs = createPathFS(null);
Path config = fs.getPath("/app/config.ini");
// Works on Windows, Mac, Linux with same code
```

### 2. Sandboxed Applications

```java
// Confine app data to specific directory
FileSystem fs = createPathFS(Paths.get("./user-data"));
Path userFile = fs.getPath("/documents/file.txt");
// Can't escape ./user-data directory
```

### 3. Testing

```java
// Each test gets its own isolated filesystem
FileSystem testFS = createPathFS(tempDirectory);
// No conflicts between tests
```

### 4. Configuration Management

```java
// Portable config paths
FileSystem fs = createPathFS(Paths.get("./config"));
Path serverConfig = fs.getPath("/servers/prod.conf");
Path dbConfig = fs.getPath("/database/connection.conf");
```

## Performance Considerations

PathFileSystem delegates to the underlying OS filesystem, so performance is similar to regular file operations:

- ✅ **No significant overhead** - Just path translation
- ✅ **Direct NIO.2 delegation** - No intermediate buffers
- ✅ **Efficient sandboxing** - Simple path prefixing

## Limitations

1. **Always uses "/"** - Cannot use "\" even on Windows (this is by design)
2. **Sandboxing is path-based** - Not true security isolation (use OS features for that)
3. **No symbolic link special handling** - Links work but may escape sandbox

## Summary

PathFileSystem provides:
- ✅ **Modern NIO.2 API** - Uses `Path` instead of `File`
- ✅ **Cross-platform normalization** - Always "/" separator
- ✅ **Sandboxing support** - Confine operations to directory
- ✅ **Transparent translation** - Automatic "/" ↔ OS separator

Perfect for:
- Cross-platform applications
- Testing with isolated filesystems
- Sandboxed data access
- Modern Java applications using NIO.2

Start using PathFileSystem for cross-platform file operations! 🚀
