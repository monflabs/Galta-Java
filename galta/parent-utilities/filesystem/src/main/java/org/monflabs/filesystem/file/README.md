# File-based FileSystem

A complete FileSystem implementation that delegates to the `java.io.File` API with optional sandboxing support. This provides a NIO.2 interface to traditional file operations with the ability to restrict access to a specific directory.

## Overview

The File-based FileSystem wraps the traditional `java.io.File` API with a modern NIO.2 interface. It can operate in two modes: unrestricted (full filesystem access) or sandboxed (restricted to a specific directory).

## Key Features

✅ **Real Persistence**: All data stored on disk  
✅ **Optional Sandboxing**: Restrict access to specific directory  
✅ **File API Integration**: Works with existing `java.io.File` code  
✅ **Path Security**: Validates paths stay within sandbox  
✅ **Home Directory Expansion**: Supports `~/path` notation  
✅ **Platform Native**: Uses OS filesystem features  
✅ **Large Files**: No artificial capacity limits  
✅ **File Conversion**: Paths can convert to `java.io.File`  

## Architecture

```
FileFileSystemProvider
    └─> Creates FileFileSystem instances
    └─> Delegates all I/O to java.io.File
    └─> Optional path validation for sandbox

FileFileSystem
    └─> Optional root File for sandboxing
    └─> Returns system roots or sandbox root
    └─> Platform separator

FilePath
    └─> Wraps java.io.File
    └─> Resolves relative to sandbox root (if set)
    └─> Can convert to File via toFile()

FileBasedFileStore
    └─> Reports actual disk space
    └─> Uses File.getTotalSpace(), etc.
```

## Modes of Operation

### Mode 1: Unrestricted Access

Full access to the entire filesystem:

```java
FileFileSystemProvider provider = new FileFileSystemProvider();
URI uri = URI.create("file-impl:///");

// No root parameter = unrestricted
try (FileSystem fs = provider.newFileSystem(uri, new HashMap<>())) {
    // Can access any file on the system
    Path file = fs.getPath("/etc/hosts");
    String content = new String(Files.readAllBytes(file));
    
    // Can write to any accessible location
    Path temp = fs.getPath("/tmp/test.txt");
    Files.write(temp, "data".getBytes());
}
```

### Mode 2: Sandboxed Access

Restricted to a specific directory:

```java
FileFileSystemProvider provider = new FileFileSystemProvider();
URI uri = URI.create("file-impl:///");

// Specify root directory
Map<String, Object> env = new HashMap<>();
env.put(FileFileSystemProvider.ROOT_PARAM, new File("/home/user/myapp"));

try (FileSystem fs = provider.newFileSystem(uri, env)) {
    // "/" now points to /home/user/myapp
    Path file = fs.getPath("/data.txt");
    // Actually: /home/user/myapp/data.txt
    Files.write(file, "content".getBytes());
    
    // Cannot escape the sandbox
    Path escape = fs.getPath("/../../../etc/passwd");
    Files.readAllBytes(escape);  // AccessDeniedException!
}
```

## Usage

### Basic File Operations

```java
FileFileSystemProvider provider = new FileFileSystemProvider();
URI uri = URI.create("file-impl:///");

try (FileSystem fs = provider.newFileSystem(uri, new HashMap<>())) {
    // Create a file
    Path file = fs.getPath("/tmp/example.txt");
    Files.write(file, "Hello, File FileSystem!".getBytes());
    
    // Read it back
    String content = new String(Files.readAllBytes(file));
    System.out.println(content);
    
    // File exists on disk
    File javaFile = ((FilePath) file).toFile();
    System.out.println("Real file: " + javaFile.getAbsolutePath());
    
    // Delete
    Files.delete(file);
}
```

### Sandboxed Operations

```java
// Create sandbox in user's home directory
File sandboxRoot = new File(System.getProperty("user.home"), "sandbox");
sandboxRoot.mkdirs();

Map<String, Object> env = new HashMap<>();
env.put(FileFileSystemProvider.ROOT_PARAM, sandboxRoot);

FileFileSystemProvider provider = new FileFileSystemProvider();
try (FileSystem fs = provider.newFileSystem(
        URI.create("file-impl:///sandbox"), env)) {
    
    System.out.println("Sandbox root: " + sandboxRoot.getAbsolutePath());
    
    // All paths relative to sandbox
    Path config = fs.getPath("/config.ini");
    Files.write(config, "setting=value".getBytes());
    
    Path data = fs.getPath("/data/users.db");
    Files.createDirectories(data.getParent());
    Files.write(data, "database content".getBytes());
    
    // List sandbox contents
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(fs.getPath("/"))) {
        for (Path entry : stream) {
            System.out.println(entry);
            System.out.println("  Real: " + ((FilePath) entry).toFile());
        }
    }
}
```

### Home Directory Expansion

```java
// Use ~ to refer to user home
Map<String, Object> env = new HashMap<>();
env.put(FileFileSystemProvider.ROOT_PARAM, "~/myapp");

try (FileSystem fs = provider.newFileSystem(uri, env)) {
    // Automatically expands to /home/username/myapp
    Path file = fs.getPath("/data.txt");
    Files.write(file, "content".getBytes());
}
```

## Components

### FileFileSystemProvider

**Scheme**: `file-impl://`

**Parameters**:
- `ROOT_PARAM` (optional): Root directory for sandboxing
  - Can be `File`, `String`, or path with `~`
  - If not provided: unrestricted access

**Responsibilities**:
- Creates FileFileSystem instances
- Validates sandbox root exists and is a directory
- Converts to canonical path for security
- Validates all operations stay within sandbox (if set)
- Delegates all I/O to `java.io.File` API

**Key Methods**:
```java
// Create filesystem
FileSystem newFileSystem(URI uri, Map<String,?> env)

// Read/Write delegated to Files API
SeekableByteChannel newByteChannel(Path path, Set<OpenOption> options, FileAttribute<?>... attrs)

// Directory operations
DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter)
void createDirectory(Path dir, FileAttribute<?>... attrs)

// File operations
void delete(Path path)
void copy(Path source, Path target, CopyOption... options)
void move(Path source, Path target, CopyOption... options)
```

**Path Validation**:
```java
private void validatePath(FilePath path) throws IOException {
    FileFileSystem fs = (FileFileSystem) path.getFileSystem();
    if (fs.getRoot() != null) {
        File file = path.toFile();
        File canonical = file.getCanonicalFile();
        File root = fs.getRoot();
        
        String canonicalPath = canonical.getAbsolutePath();
        String rootPath = root.getAbsolutePath();
        
        if (!canonicalPath.startsWith(rootPath)) {
            throw new AccessDeniedException("Path escapes filesystem root");
        }
    }
}
```

### FileFileSystem

**Properties**:
- Optional `root` File for sandboxing
- Platform-specific separator (`File.separator`)
- Not read-only

**Root Directories**:
```java
// Without sandbox
Iterable<Path> getRootDirectories()
// Returns all system roots: C:\, D:\, etc. (Windows) or / (Unix)

// With sandbox
Iterable<Path> getRootDirectories()
// Returns single virtual root "/"
```

**Key Methods**:
```java
File getRoot()                    // Get sandbox root (may be null)
String getSeparator()             // Platform separator
Iterable<Path> getRootDirectories()
Iterable<FileStore> getFileStores()
```

### FilePath

**Implementation**: Wraps `java.io.File`

**Path Resolution**:
```java
// Without sandbox
Path path = fs.getPath("/tmp/file.txt");
File file = ((FilePath) path).toFile();
// Result: /tmp/file.txt

// With sandbox (root = /home/user/app)
Path path = fs.getPath("/data.txt");
File file = ((FilePath) path).toFile();
// Result: /home/user/app/data.txt
```

**Key Methods**:
```java
// Path operations (inherited from AbstractPath)
Path resolve(Path other)
Path relativize(Path other)
Path normalize()
Path getParent()
Path getFileName()

// File-specific
File toFile()                    // Convert to java.io.File
Path toAbsolutePath()           // Absolute path
Path toRealPath(LinkOption...)  // Canonical path with validation
URI toUri()                      // file-impl:/// URI
```

### FileBasedFileStore

**Purpose**: Report disk space information

**Properties**:
```java
String name()          // Root directory path
String type()          // "file"
boolean isReadOnly()   // Depends on disk

// Space information (delegated to File API)
long getTotalSpace()      // Total disk space
long getUsableSpace()     // Available to JVM
long getUnallocatedSpace() // Unallocated on disk
```

## Sandboxing

### Setting Up a Sandbox

```java
// Option 1: Using File object
File sandboxRoot = new File("/var/app/data");

Map<String, Object> env = new HashMap<>();
env.put(FileFileSystemProvider.ROOT_PARAM, sandboxRoot);

// Option 2: Using String path
env.put(FileFileSystemProvider.ROOT_PARAM, "/var/app/data");

// Option 3: Using home directory
env.put(FileFileSystemProvider.ROOT_PARAM, "~/myapp/data");

FileFileSystemProvider provider = new FileFileSystemProvider();
FileSystem fs = provider.newFileSystem(
    URI.create("file-impl:///app"), env
);
```

### Path Resolution in Sandbox

```java
// Sandbox root: /home/user/project

FileSystem fs = createSandboxedFS("/home/user/project");

Path file1 = fs.getPath("/README.md");
// Maps to: /home/user/project/README.md

Path file2 = fs.getPath("/src/Main.java");
// Maps to: /home/user/project/src/Main.java

Path file3 = fs.getPath("/");
// Maps to: /home/user/project
```

### Security Validation

All file operations validate paths:

```java
Path escape = fs.getPath("/../../../etc/passwd");

// Any operation will throw
Files.readAllBytes(escape);        // AccessDeniedException
Files.write(escape, data);         // AccessDeniedException
Files.exists(escape);              // AccessDeniedException (after validation)
```

**Validation Process**:
1. Convert path to File
2. Get canonical file (resolves symlinks, `..`, etc.)
3. Check if canonical path starts with sandbox root
4. Throw `AccessDeniedException` if outside

### Symlink Handling

```java
// If sandbox contains symlink pointing outside
File sandboxRoot = new File("/home/user/sandbox");
// sandbox/link -> /etc/passwd (symlink)

FileSystem fs = createSandboxedFS(sandboxRoot);
Path link = fs.getPath("/link");

// Validation catches this
Files.readAllBytes(link);  // AccessDeniedException
// Because canonical path is /etc/passwd (outside sandbox)
```

## File Operations

### Creating Files

```java
Path file = fs.getPath("/documents/report.txt");

// Create parent directories if needed
Files.createDirectories(file.getParent());

// Write content
Files.write(file, "Report content".getBytes());

// Or use output stream
try (OutputStream out = Files.newOutputStream(file)) {
    out.write("More content".getBytes());
}
```

### Reading Files

```java
Path file = fs.getPath("/data.txt");

// Read all at once
byte[] content = Files.readAllBytes(file);
String text = new String(content);

// Read lines
List<String> lines = Files.readAllLines(file);

// Use input stream
try (InputStream in = Files.newInputStream(file)) {
    int data = in.read();
    // ...
}

// Use buffered reader
try (BufferedReader reader = Files.newBufferedReader(file)) {
    String line;
    while ((line = reader.readLine()) != null) {
        System.out.println(line);
    }
}
```

### Appending to Files

```java
Path log = fs.getPath("/application.log");

// Append mode
Files.write(log, "Log entry 1\n".getBytes(), 
    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    
Files.write(log, "Log entry 2\n".getBytes(), 
    StandardOpenOption.APPEND);
```

### Deleting Files

```java
Path file = fs.getPath("/temp.txt");

// Delete (throws if doesn't exist)
Files.delete(file);

// Delete if exists (returns boolean)
boolean deleted = Files.deleteIfExists(file);
```

### File Attributes

```java
Path file = fs.getPath("/document.pdf");

// Check existence
boolean exists = Files.exists(file);

// Get size
long size = Files.size(file);

// Get modification time
FileTime modified = Files.getLastModifiedTime(file);

// Get all attributes
BasicFileAttributes attrs = Files.readAttributes(
    file, BasicFileAttributes.class
);

System.out.println("Size: " + attrs.size());
System.out.println("Created: " + attrs.creationTime());
System.out.println("Modified: " + attrs.lastModifiedTime());
System.out.println("Is directory: " + attrs.isDirectory());
```

### File Permissions

```java
Path file = fs.getPath("/script.sh");

// Check permissions
boolean readable = Files.isReadable(file);
boolean writable = Files.isWritable(file);
boolean executable = Files.isExecutable(file);

// Check if hidden
boolean hidden = Files.isHidden(file);

// Set permissions (platform-dependent)
File javaFile = ((FilePath) file).toFile();
javaFile.setReadable(true);
javaFile.setWritable(true);
javaFile.setExecutable(true);
```

## Directory Operations

### Creating Directories

```java
// Create single directory
Path dir = fs.getPath("/documents");
Files.createDirectory(dir);

// Create nested directories
Path nested = fs.getPath("/data/2024/january");
Files.createDirectories(nested);  // Creates all levels
```

### Listing Directories

```java
Path dir = fs.getPath("/documents");

// List all entries
try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
    for (Path entry : stream) {
        String type = Files.isDirectory(entry) ? "DIR" : "FILE";
        long size = Files.isDirectory(entry) ? 0 : Files.size(entry);
        System.out.println(entry.getFileName() + " [" + type + "] " + size + " bytes");
    }
}

// Filter by pattern
try (DirectoryStream<Path> stream = 
        Files.newDirectoryStream(dir, "*.{txt,pdf}")) {
    for (Path entry : stream) {
        System.out.println(entry);
    }
}

// Filter with custom filter
try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, 
        entry -> Files.size(entry) > 1024)) {
    for (Path entry : stream) {
        System.out.println(entry.getFileName() + ": " + Files.size(entry));
    }
}
```

### Walking Directory Trees

```java
Path root = fs.getPath("/project");

// Find all Java files
List<Path> javaFiles = Files.walk(root)
    .filter(p -> p.toString().endsWith(".java"))
    .collect(Collectors.toList());

// Calculate total size
long totalSize = Files.walk(root)
    .filter(Files::isRegularFile)
    .mapToLong(p -> {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0;
        }
    })
    .sum();

System.out.println("Total size: " + totalSize + " bytes");

// Walk with visitor
Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        System.out.println("File: " + file);
        return FileVisitResult.CONTINUE;
    }
    
    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        System.out.println("Directory: " + dir);
        return FileVisitResult.CONTINUE;
    }
});
```

### Deleting Directories

```java
Path dir = fs.getPath("/temp");

// Delete empty directory
Files.delete(dir);

// Delete recursively
Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) 
            throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
    }
    
    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) 
            throws IOException {
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
    }
});
```

## Copy and Move Operations

### Copying Files

```java
Path source = fs.getPath("/original.txt");
Path target = fs.getPath("/copy.txt");

// Simple copy
Files.copy(source, target);

// Copy with options
Files.copy(source, target, 
    StandardCopyOption.REPLACE_EXISTING,
    StandardCopyOption.COPY_ATTRIBUTES);

// Copy to different filesystem
Path externalTarget = Paths.get("/tmp/external-copy.txt");
Files.copy(source, externalTarget);
```

### Moving Files

```java
Path source = fs.getPath("/old-location.txt");
Path target = fs.getPath("/new-location.txt");

// Move (rename)
Files.move(source, target);

// Move with options
Files.move(source, target, 
    StandardCopyOption.REPLACE_EXISTING,
    StandardCopyOption.ATOMIC_MOVE);  // If supported by OS
```

### Copying Directories

```java
Path sourceDir = fs.getPath("/source");
Path targetDir = fs.getPath("/target");

// Recursive copy
Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) 
            throws IOException {
        Path targetPath = targetDir.resolve(sourceDir.relativize(dir));
        Files.createDirectories(targetPath);
        return FileVisitResult.CONTINUE;
    }
    
    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) 
            throws IOException {
        Path targetPath = targetDir.resolve(sourceDir.relativize(file));
        Files.copy(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
        return FileVisitResult.CONTINUE;
    }
});
```

## Integration with java.io.File

### Converting to File

```java
Path path = fs.getPath("/data.txt");

// Get File object
File file = ((FilePath) path).toFile();

// Now can use File API
boolean exists = file.exists();
long length = file.length();
long lastModified = file.lastModified();

// Use with legacy APIs
FileInputStream fis = new FileInputStream(file);
FileOutputStream fos = new FileOutputStream(file);
```

### Working with Existing File Code

```java
public class LegacyFileProcessor {
    public void processFile(File file) {
        // Legacy code using java.io.File
    }
}

// Bridge to NIO.2
FileSystem fs = createFileFileSystem();
Path path = fs.getPath("/document.pdf");

LegacyFileProcessor processor = new LegacyFileProcessor();
processor.processFile(((FilePath) path).toFile());
```

## Use Cases

### 1. Application Data Directory

```java
public class AppDataManager {
    private final FileSystem dataFS;
    
    public AppDataManager(String appName) throws IOException {
        // Create app data directory
        File appData = new File(System.getProperty("user.home"), 
            ".config/" + appName);
        appData.mkdirs();
        
        // Create sandboxed filesystem
        FileFileSystemProvider provider = new FileFileSystemProvider();
        Map<String, Object> env = new HashMap<>();
        env.put(FileFileSystemProvider.ROOT_PARAM, appData);
        
        this.dataFS = provider.newFileSystem(
            URI.create("file-impl:///" + appName), env
        );
    }
    
    public void saveConfig(String key, String value) throws IOException {
        Path configFile = dataFS.getPath("/config/" + key + ".conf");
        Files.createDirectories(configFile.getParent());
        Files.write(configFile, value.getBytes());
    }
    
    public String loadConfig(String key) throws IOException {
        Path configFile = dataFS.getPath("/config/" + key + ".conf");
        if (!Files.exists(configFile)) {
            return null;
        }
        return new String(Files.readAllBytes(configFile));
    }
    
    public void close() throws IOException {
        dataFS.close();
    }
}
```

### 2. User File Upload Sandbox

```java
public class UserFileManager {
    private final Map<String, FileSystem> userFilesystems = new HashMap<>();
    private final FileFileSystemProvider provider = new FileFileSystemProvider();
    private final File baseDir = new File("/var/app/user-files");
    
    public FileSystem getUserFilesystem(String userId) throws IOException {
        if (!userFilesystems.containsKey(userId)) {
            File userDir = new File(baseDir, userId);
            userDir.mkdirs();
            
            Map<String, Object> env = new HashMap<>();
            env.put(FileFileSystemProvider.ROOT_PARAM, userDir);
            
            FileSystem fs = provider.newFileSystem(
                URI.create("file-impl:///user-" + userId), env
            );
            userFilesystems.put(userId, fs);
        }
        return userFilesystems.get(userId);
    }
    
    public void uploadFile(String userId, String filename, byte[] content) 
            throws IOException {
        FileSystem userFS = getUserFilesystem(userId);
        Path uploadPath = userFS.getPath("/uploads/" + filename);
        
        // Validate filename (no path traversal)
        if (filename.contains("..") || filename.contains("/") || 
            filename.contains("\\")) {
            throw new IllegalArgumentException("Invalid filename");
        }
        
        Files.createDirectories(uploadPath.getParent());
        Files.write(uploadPath, content);
    }
    
    public byte[] downloadFile(String userId, String filename) throws IOException {
        FileSystem userFS = getUserFilesystem(userId);
        Path downloadPath = userFS.getPath("/uploads/" + filename);
        
        if (!Files.exists(downloadPath)) {
            throw new NoSuchFileException("File not found");
        }
        
        return Files.readAllBytes(downloadPath);
    }
}
```

### 3. Project Workspace Management

```java
public class ProjectWorkspace {
    private final FileSystem projectFS;
    private final Path projectRoot;
    
    public ProjectWorkspace(File projectDirectory) throws IOException {
        if (!projectDirectory.exists()) {
            projectDirectory.mkdirs();
        }
        
        FileFileSystemProvider provider = new FileFileSystemProvider();
        Map<String, Object> env = new HashMap<>();
        env.put(FileFileSystemProvider.ROOT_PARAM, projectDirectory);
        
        this.projectFS = provider.newFileSystem(
            URI.create("file-impl:///project"), env
        );
        this.projectRoot = projectFS.getPath("/");
        
        initializeStructure();
    }
    
    private void initializeStructure() throws IOException {
        Files.createDirectories(projectFS.getPath("/src"));
        Files.createDirectories(projectFS.getPath("/test"));
        Files.createDirectories(projectFS.getPath("/docs"));
        Files.createDirectories(projectFS.getPath("/build"));
    }
    
    public Path getSourcePath(String relativePath) {
        return projectFS.getPath("/src").resolve(relativePath);
    }
    
    public Path getTestPath(String relativePath) {
        return projectFS.getPath("/test").resolve(relativePath);
    }
    
    public List<Path> findSourceFiles(String extension) throws IOException {
        return Files.walk(projectFS.getPath("/src"))
            .filter(p -> p.toString().endsWith(extension))
            .collect(Collectors.toList());
    }
    
    public void cleanBuildDirectory() throws IOException {
        Path buildDir = projectFS.getPath("/build");
        if (Files.exists(buildDir)) {
            Files.walkFileTree(buildDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) 
                        throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) 
                        throws IOException {
                    if (!dir.equals(buildDir)) {
                        Files.delete(dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
```

### 4. Testing with Real Files

```java
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class FileProcessorTest {
    private FileSystem testFS;
    private Path testRoot;
    
    @BeforeAll
    public void setUp() throws IOException {
        // Create temp directory for all tests
        testRoot = Files.createTempDirectory("file-processor-test");
        
        // Create sandboxed filesystem
        FileFileSystemProvider provider = new FileFileSystemProvider();
        Map<String, Object> env = new HashMap<>();
        env.put(FileFileSystemProvider.ROOT_PARAM, testRoot.toFile());
        
        testFS = provider.newFileSystem(
            URI.create("file-impl:///test"), env
        );
    }
    
    @AfterAll
    public void tearDown() throws IOException {
        testFS.close();
        
        // Clean up test directory
        Files.walkFileTree(testRoot, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) 
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) 
                    throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
    
    @BeforeEach
    public void setUpTest() throws IOException {
        // Clean filesystem between tests
        try (DirectoryStream<Path> stream = 
                Files.newDirectoryStream(testFS.getPath("/"))) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    deleteRecursively(entry);
                } else {
                    Files.delete(entry);
                }
            }
        }
    }
    
    @Test
    public void testFileProcessing() throws IOException {
        // Create test input
        Path input = testFS.getPath("/input.txt");
        Files.write(input, "test data".getBytes());
        
        // Process
        FileProcessor processor = new FileProcessor(testFS);
        processor.process("/input.txt", "/output.txt");
        
        // Verify
        Path output = testFS.getPath("/output.txt");
        assertTrue(Files.exists(output));
        
        String result = new String(Files.readAllBytes(output));
        assertEquals("PROCESSED: test data", result);
    }
}
```

### 5. Log File Management

```java
public class LogFileManager {
    private final FileSystem logFS;
    private final Path logsDir;
    
    public LogFileManager(File logDirectory) throws IOException {
        logDirectory.mkdirs();
        
        FileFileSystemProvider provider = new FileFileSystemProvider();
        Map<String, Object> env = new HashMap<>();
        env.put(FileFileSystemProvider.ROOT_PARAM, logDirectory);
        
        this.logFS = provider.newFileSystem(
            URI.create("file-impl:///logs"), env
        );
        this.logsDir = logFS.getPath("/");
    }
    
    public void writeLog(String category, String message) throws IOException {
        String date = LocalDate.now().toString();
        Path logFile = logsDir.resolve(category + "-" + date + ".log");
        
        String timestamp = LocalDateTime.now().toString();
        String logEntry = timestamp + " - " + message + "\n";
        
        Files.write(logFile, logEntry.getBytes(), 
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    
    public List<String> readLog(String category, LocalDate date) throws IOException {
        Path logFile = logsDir.resolve(category + "-" + date + ".log");
        
        if (!Files.exists(logFile)) {
            return Collections.emptyList();
        }
        
        return Files.readAllLines(logFile);
    }
    
    public void archiveOldLogs(int daysToKeep) throws IOException {
        LocalDate cutoffDate = LocalDate.now().minusDays(daysToKeep);
        Path archiveDir = logsDir.resolve("archive");
        Files.createDirectories(archiveDir);
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(logsDir, "*.log")) {
            for (Path logFile : stream) {
                String filename = logFile.getFileName().toString();
                // Parse date from filename
                String dateStr = filename.substring(filename.lastIndexOf('-') + 1, 
                    filename.lastIndexOf('.'));
                LocalDate logDate = LocalDate.parse(dateStr);
                
                if (logDate.isBefore(cutoffDate)) {
                    Path archived = archiveDir.resolve(filename);
                    Files.move(logFile, archived);
                }
            }
        }
    }
    
    public void compressArchive() throws IOException {
        Path archiveDir = logsDir.resolve("archive");
        if (!Files.exists(archiveDir)) {
            return;
        }
        
        Path zipFile = logsDir.resolve("archive-" + LocalDate.now() + ".zip");
        
        try (FileOutputStream fos = new FileOutputStream(
                ((FilePath) zipFile).toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            
            Files.walkFileTree(archiveDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) 
                        throws IOException {
                    String entryName = archiveDir.relativize(file).toString();
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        
        // Delete archived files after compression
        Files.walkFileTree(archiveDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) 
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) 
                    throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
```

## Performance

### Speed Characteristics

File-based filesystem performance depends on:
- Disk type (SSD vs HDD)
- File size
- Number of files
- Caching

**Typical Performance (SSD)**:

| Operation | Time (1000 files, 1KB each) |
|-----------|------------------------------|
| Create | ~100ms |
| Read | ~50ms |
| Write | ~100ms |
| Delete | ~50ms |
| List directory | ~10ms |

**Compared to Memory FS**:
- 5-10x slower (but still fast on SSD)
- Persistent (survives JVM restart)
- No capacity limit (disk size)

### Optimization Tips

```java
// 1. Batch operations
List<Path> files = new ArrayList<>();
for (int i = 0; i < 1000; i++) {
    files.add(fs.getPath("/file" + i + ".txt"));
}

// Better than individual writes
for (Path file : files) {
    Files.write(file, data);
}

// 2. Use buffered I/O for large files
try (BufferedOutputStream out = new BufferedOutputStream(
        Files.newOutputStream(largeFile))) {
    // Write in chunks
}

// 3. Reuse FileSystem instances
// Don't create new filesystem for each operation

// 4. Close streams promptly
try (InputStream in = Files.newInputStream(file)) {
    // Read data
} // Automatically closed
```

## Limitations

### 1. Platform-Dependent

```java
// Path separators differ
// Windows: \ 
// Unix: /

// File permissions differ
// Unix: rwxrwxrwx
// Windows: Different model

// Case sensitivity differs
// Unix: file.txt ≠ File.txt
// Windows: file.txt = File.txt (usually)
```

### 2. Symbolic Links (in Sandbox)

```java
// Symlinks can escape sandbox if not careful
// Validation uses canonical paths to prevent this

Path link = fs.getPath("/link-to-outside");
// If link points outside sandbox:
Files.readAllBytes(link);  // AccessDeniedException
```

### 3. No Watch Service

```java
// Not implemented
WatchService watcher = fs.newWatchService();
// Throws: UnsupportedOperationException
```

### 4. Disk Space

```java
// Limited by disk capacity
// Can run out of space

try {
    Files.write(file, hugeData);
} catch (IOException e) {
    // Could be out of disk space
}
```

### 5. I/O Errors

```java
// Subject to I/O errors:
// - Disk failures
// - Network filesystem issues
// - Permission problems
// - File locking conflicts
```

## Best Practices

### 1. Always Use Sandboxing for Untrusted Input

```java
// Good
File userDir = new File("/var/app/users/" + sanitizeUserId(userId));
Map<String, Object> env = new HashMap<>();
env.put(FileFileSystemProvider.ROOT_PARAM, userDir);
FileSystem fs = provider.newFileSystem(uri, env);

// Bad (security risk)
FileSystem fs = provider.newFileSystem(uri, new HashMap<>());
Path userFile = fs.getPath("/var/app/users/" + userId + "/" + userInput);
```

### 2. Validate Root Directory

```java
File root = new File(rootPath);

// Check exists
if (!root.exists()) {
    throw new IllegalArgumentException("Root does not exist: " + root);
}

// Check is directory
if (!root.isDirectory()) {
    throw new IllegalArgumentException("Root is not a directory: " + root);
}

// Use canonical path
root = root.getCanonicalFile();
env.put(ROOT_PARAM, root);
```

### 3. Use try-with-resources

```java
// Good
try (FileSystem fs = provider.newFileSystem(uri, env)) {
    // Use filesystem
} // Automatically closed

// Bad
FileSystem fs = provider.newFileSystem(uri, env);
// ... use filesystem ...
// Forgot to close!
```

### 4. Handle I/O Exceptions

```java
try {
    Path file = fs.getPath("/data.txt");
    byte[] content = Files.readAllBytes(file);
} catch (NoSuchFileException e) {
    // File doesn't exist
} catch (AccessDeniedException e) {
    // No permission or outside sandbox
} catch (IOException e) {
    // Other I/O error
}
```

### 5. Use Canonical Paths for Security

```java
// When setting up sandbox
File root = new File(userInput);
root = root.getCanonicalFile();  // Resolves symlinks, ..

env.put(ROOT_PARAM, root);
```

## Comparison with Alternatives

### vs Memory FileSystem

| Aspect | File-based | Memory |
|--------|-----------|--------|
| Persistence | **Yes** | No |
| Speed | Moderate (disk) | **Very fast** |
| Capacity | **Disk size** | 1 GB |
| Cleanup | **Manual** | Automatic |
| Testing | Slower | **Faster** |
| Production | **Yes** | No |

### vs Default FileSystem

| Aspect | File-based | Default (Paths.get) |
|--------|-----------|---------------------|
| Sandboxing | **Optional** | No |
| Security | **Validated** | Manual |
| API | NIO.2 | NIO.2 |
| Performance | Same | Same |
| Use case | **Sandboxed apps** | General use |

### vs Path-Delegating

| Aspect | File-based | Path-Delegating |
|--------|-----------|-----------------|
| Root type | File/String | **Any Path** |
| Flexibility | Real FS only | **Any FS** |
| toFile() | **Yes** | No |
| Composable | No | **Yes** |
| Use case | File sandboxing | **Universal** |

## Summary

The File-based FileSystem is ideal for:

✅ **Real file persistence** - Data survives JVM restart  
✅ **Application sandboxing** - Restrict access to directory  
✅ **User file management** - Isolate user data  
✅ **Project workspaces** - Organize project files  
✅ **Legacy integration** - Works with `java.io.File` code  
✅ **Production use** - Stable and reliable  

**Key Features:**
- 🔒 **Optional sandboxing** with security validation
- 💾 **Persistent storage** on disk
- 🔄 **File API integration** via `toFile()`
- 📁 **Platform native** features
- ♾️ **No capacity limit** (disk size)

Use File-based FileSystem when you need real file persistence with optional sandboxing for security!
