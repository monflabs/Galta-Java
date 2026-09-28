## ResourceFileSystem - Read-Only Classpath Resource Access

## Overview

`ResourceFileSystem` provides read-only access to classpath resources through the standard Java NIO.2 filesystem API. Since ClassLoaders cannot enumerate resources, it uses a **manifest file** that lists all available resources.

**Key Features:**
- ✅ Read-only access to classpath resources
- ✅ Standard NIO.2 Path API
- ✅ Manifest-based resource enumeration
- ✅ Configurable base path
- ✅ Directory navigation with implicit directories
- ✅ Pattern matching (glob/regex)
- ✅ Works with any ClassLoader

## Quick Start

### 1. Create a Manifest File

Create `resources.manifest` in your resources directory:

```
# List all resources to expose
config/app.properties
config/database.properties
templates/email.html
templates/welcome.html
data/users.csv
images/logo.png
```

### 2. Use the Filesystem

```java
ResourceFileSystemProvider provider = new ResourceFileSystemProvider();
Map<String, Object> env = new HashMap<>();
env.put(ResourceFileSystemProvider.BASE_PATH_PARAM, "myapp/");

try (FileSystem fs = provider.newFileSystem(
        URI.create("resource:///myapp"), env)) {
    
    // Read a file
    Path config = fs.getPath("/config/app.properties");
    String content = Files.readString(config);
    
    // List directory
    try (DirectoryStream<Path> stream = 
            Files.newDirectoryStream(fs.getPath("/config"))) {
        for (Path entry : stream) {
            System.out.println(entry);
        }
    }
}
```

## Manifest File Format

The manifest file (`resources.manifest` by default) lists all resources, one per line:

```
# Comments start with #
# Empty lines are ignored

# Files
file1.txt
file2.txt

# Files in subdirectories
config/app.properties
config/db.properties
templates/email/welcome.html
templates/email/goodbye.html

# Leading slashes are optional (automatically normalized)
/data/users.csv
data/products.csv
```

### Manifest Rules

1. **One path per line**
2. **Paths are relative** to the base path
3. **No leading slash** (or it's removed automatically)
4. **Comments** start with `#`
5. **Empty lines** are ignored
6. **Directories** are implicit (auto-detected from file paths)

## Configuration Parameters

### Required Parameters
None! All parameters have sensible defaults.

### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `classLoader` | ClassLoader | Context or System | ClassLoader to use for loading resources |
| `basePath` | String | `""` (empty) | Base path prefix for all resources |
| `manifestFile` | String | `"resources.manifest"` | Name of the manifest file |

## Usage Examples

### Example 1: Application Configuration

**Directory Structure:**
```
src/main/resources/
  config/
    resources.manifest
    app.properties
    database.properties
    logging.xml
```

**resources.manifest:**
```
app.properties
database.properties
logging.xml
```

**Code:**
```java
ResourceFileSystemProvider provider = new ResourceFileSystemProvider();
Map<String, Object> env = Map.of(
    ResourceFileSystemProvider.BASE_PATH_PARAM, "config/"
);

try (FileSystem fs = provider.newFileSystem(
        URI.create("resource:///config"), env)) {
    
    Properties appProps = new Properties();
    try (InputStream in = Files.newInputStream(
            fs.getPath("/app.properties"))) {
        appProps.load(in);
    }
    
    System.out.println("App name: " + appProps.getProperty("app.name"));
}
```

### Example 2: Template Management

**Directory Structure:**
```
src/main/resources/
  templates/
    resources.manifest
    email/
      welcome.html
      goodbye.html
    web/
      home.html
      about.html
```

**resources.manifest:**
```
email/welcome.html
email/goodbye.html
web/home.html
web/about.html
```

**Code:**
```java
Map<String, Object> env = Map.of(
    ResourceFileSystemProvider.BASE_PATH_PARAM, "templates/"
);

try (FileSystem fs = provider.newFileSystem(
        URI.create("resource:///templates"), env)) {
    
    // Load email template
    Path template = fs.getPath("/email/welcome.html");
    String html = Files.readString(template);
    
    // Process template
    String personalizedEmail = html.replace("{{name}}", userName);
}
```

### Example 3: Static File Server

**Directory Structure:**
```
src/main/resources/
  static/
    resources.manifest
    css/
      style.css
      theme.css
    js/
      app.js
      utils.js
    images/
      logo.png
      banner.jpg
```

**resources.manifest:**
```
css/style.css
css/theme.css
js/app.js
js/utils.js
images/logo.png
images/banner.jpg
```

**Code:**
```java
Map<String, Object> env = Map.of(
    ResourceFileSystemProvider.BASE_PATH_PARAM, "static/"
);

try (FileSystem fs = provider.newFileSystem(
        URI.create("resource:///static"), env)) {
    
    // Serve a file
    String requestPath = "/css/style.css";
    Path file = fs.getPath(requestPath);
    
    if (Files.exists(file) && Files.isRegularFile(file)) {
        byte[] content = Files.readAllBytes(file);
        response.setContentType(getContentType(requestPath));
        response.getOutputStream().write(content);
    } else {
        response.sendError(404);
    }
}
```

### Example 4: Pattern Matching

```java
try (FileSystem fs = openResourceFS("documents/")) {
    // Find all PDF files
    PathMatcher pdfMatcher = fs.getPathMatcher("glob:**.pdf");
    
    Files.walk(fs.getPath("/"))
        .filter(Files::isRegularFile)
        .filter(pdfMatcher::matches)
        .forEach(path -> {
            System.out.println("Found PDF: " + path);
        });
    
    // Find all files in reports directory
    PathMatcher reportMatcher = fs.getPathMatcher("glob:reports/*.{pdf,xlsx}");
    
    Files.walk(fs.getPath("/"))
        .filter(reportMatcher::matches)
        .forEach(this::processReport);
}
```

### Example 5: Copy Resources to Disk

```java
try (FileSystem resourceFS = openResourceFS("exports/")) {
    Path outputDir = Paths.get("/tmp/extracted");
    Files.createDirectories(outputDir);
    
    // Copy all resources to disk
    Files.walk(resourceFS.getPath("/"))
        .filter(Files::isRegularFile)
        .forEach(source -> {
            try {
                Path target = outputDir.resolve(source.toString().substring(1));
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
}
```

## Implicit Directories

ResourceFileSystem automatically detects directories from file paths:

**Manifest:**
```
dir1/file1.txt
dir1/file2.txt
dir1/subdir/file3.txt
```

**Implicit Directories Created:**
- `/` (root)
- `/dir1/`
- `/dir1/subdir/`

**Usage:**
```java
Files.isDirectory(fs.getPath("/dir1"));        // true
Files.isDirectory(fs.getPath("/dir1/subdir")); // true

// List directory works
try (DirectoryStream<Path> stream = 
        Files.newDirectoryStream(fs.getPath("/dir1"))) {
    for (Path entry : stream) {
        System.out.println(entry); // file1.txt, file2.txt, subdir
    }
}
```

## ClassLoader Configuration

### Using Default ClassLoader

```java
// Uses Thread.currentThread().getContextClassLoader()
// or ResourceFileSystemProvider.class.getClassLoader()
Map<String, Object> env = Map.of(
    ResourceFileSystemProvider.BASE_PATH_PARAM, "data/"
);

FileSystem fs = provider.newFileSystem(uri, env);
```

### Using Custom ClassLoader

```java
ClassLoader customLoader = MyClass.class.getClassLoader();

Map<String, Object> env = Map.of(
    ResourceFileSystemProvider.CLASSLOADER_PARAM, customLoader,
    ResourceFileSystemProvider.BASE_PATH_PARAM, "data/"
);

FileSystem fs = provider.newFileSystem(uri, env);
```

### Using Module ClassLoader (Java 9+)

```java
ClassLoader moduleLoader = getClass().getModule().getClassLoader();

Map<String, Object> env = Map.of(
    ResourceFileSystemProvider.CLASSLOADER_PARAM, moduleLoader,
    ResourceFileSystemProvider.BASE_PATH_PARAM, "module-resources/"
);

FileSystem fs = provider.newFileSystem(uri, env);
```

## Base Path

The base path is prepended to all resource lookups:

### Without Base Path

**Manifest location:** `resources.manifest` (root of classpath)  
**Resource lookup:** `file1.txt`

```java
Map<String, Object> env = Map.of(); // No base path

// Loads: resources.manifest
// Reads: file1.txt
```

### With Base Path

**Manifest location:** `config/resources.manifest`  
**Resource lookup:** `config/file1.txt`

```java
Map<String, Object> env = Map.of(
    ResourceFileSystemProvider.BASE_PATH_PARAM, "config/"
);

// Loads: config/resources.manifest
// Reads: config/file1.txt
```

## Generating Manifest Files

### Manual Creation

```bash
# List all files
find src/main/resources/static -type f | \
    sed 's|src/main/resources/static/||' > \
    src/main/resources/static/resources.manifest
```

### Maven Plugin (Example)

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <executions>
        <execution>
            <phase>process-resources</phase>
            <goals><goal>exec</goal></goals>
            <configuration>
                <executable>sh</executable>
                <arguments>
                    <argument>-c</argument>
                    <argument>
                        cd target/classes/static &amp;&amp;
                        find . -type f ! -name resources.manifest | 
                        sed 's|^./||' > resources.manifest
                    </argument>
                </arguments>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Gradle Task (Example)

```gradle
task generateManifest {
    doLast {
        def staticDir = file("src/main/resources/static")
        def manifest = file("src/main/resources/static/resources.manifest")
        
        def files = []
        staticDir.eachFileRecurse(FileType.FILES) { file ->
            if (file.name != 'resources.manifest') {
                def relativePath = staticDir.relativePath(file)
                files << relativePath
            }
        }
        
        manifest.text = files.join('\n')
    }
}

processResources.dependsOn generateManifest
```

### Java Code Generation

```java
public static void generateManifest(Path resourceDir, Path manifestFile) 
        throws IOException {
    List<String> files = new ArrayList<>();
    
    Files.walk(resourceDir)
        .filter(Files::isRegularFile)
        .filter(p -> !p.getFileName().toString().equals("resources.manifest"))
        .forEach(p -> {
            String relative = resourceDir.relativize(p).toString()
                .replace('\\', '/');
            files.add(relative);
        });
    
    Files.write(manifestFile, files);
}
```

## Read-Only Enforcement

All write operations throw `ReadOnlyFileSystemException`:

```java
try (FileSystem fs = openResourceFS("data/")) {
    Path file = fs.getPath("/new.txt");
    
    // All of these throw ReadOnlyFileSystemException:
    Files.write(file, "data".getBytes());         // ❌
    Files.delete(file);                           // ❌
    Files.createDirectory(fs.getPath("/newdir")); // ❌
    Files.move(file, fs.getPath("/other.txt"));   // ❌
    
    // Reading is allowed:
    byte[] content = Files.readAllBytes(file);    // ✅
}
```

## Use Cases

### 1. Application Configuration
Bundle configuration files and access them through Path API.

### 2. Template Engine
Store HTML/text templates and load them dynamically.

### 3. Static Web Assets
Serve CSS, JS, images from classpath resources.

### 4. Test Fixtures
Package test data files and access them in tests.

### 5. Documentation
Embed help files, README, etc. and read them at runtime.

### 6. Data Files
Include CSV, JSON, XML data files in application.

### 7. Localization
Store translation files and load based on locale.

### 8. Resource Catalog
Enumerate and manage bundled resources programmatically.

## Performance Characteristics

### Manifest Loading
- **One-time cost**: Parsed when filesystem is opened
- **Memory**: O(n) where n is number of resources
- **Fast**: Simple text parsing

### Resource Reading
- **Uses ClassLoader**: Standard `getResourceAsStream()`
- **Buffered**: Resources read into memory (like ZipFileSystem)
- **Efficient**: No disk I/O after first load

### Directory Listing
- **In-memory scan**: O(n) where n is total resources
- **Filtered**: Only returns direct children
- **Cached**: Manifest loaded once

## Limitations

### 1. No Resource Enumeration
Cannot discover resources not listed in manifest.

### 2. Manifest Must Be Complete
All resources must be listed explicitly.

### 3. Read-Only
Cannot modify, create, or delete resources.

### 4. No Watch Service
Cannot monitor resources for changes.

### 5. Memory-Based Reading
Resources loaded entirely into memory (not streaming).

### 6. No toFile() Conversion
Cannot convert resource paths to File objects.

## Comparison with Other Approaches

| Approach | Pros | Cons |
|----------|------|------|
| **ResourceFileSystem** | Standard Path API, directory navigation, pattern matching | Requires manifest |
| **getResourceAsStream()** | Simple, direct | No enumeration, no Path API |
| **ClassLoader.getResources()** | Can find resources | Returns URLs not Paths, no directory structure |
| **ZipFileSystem (jar:)** | Full filesystem | Only works with JARs, not classpath |

## Best Practices

### 1. Generate Manifest at Build Time
Don't manually maintain the manifest - generate it from your actual resources.

### 2. Use Base Path for Organization
```java
// Good - organized by purpose
openResourceFS("config/")
openResourceFS("templates/")
openResourceFS("static/")

// Bad - everything in root
openResourceFS("")
```

### 3. Cache Filesystem Instance
```java
// Good - reuse filesystem
private static final FileSystem RESOURCE_FS = openResourceFS("data/");

public String loadFile(String path) throws IOException {
    return Files.readString(RESOURCE_FS.getPath(path));
}

// Bad - opens repeatedly
public String loadFile(String path) throws IOException {
    try (FileSystem fs = openResourceFS("data/")) {
        return Files.readString(fs.getPath(path));
    }
}
```

### 4. Close When Done
```java
// Always use try-with-resources
try (FileSystem fs = openResourceFS("data/")) {
    // Use filesystem
}
```

### 5. Handle Missing Resources
```java
Path resource = fs.getPath("/config.txt");
if (Files.exists(resource)) {
    String content = Files.readString(resource);
} else {
    // Use default configuration
    String content = getDefaultConfig();
}
```

## Summary

**ResourceFileSystem** provides:
- ✅ Standard NIO.2 Path API for classpath resources
- ✅ Manifest-based resource enumeration
- ✅ Directory navigation with implicit directories
- ✅ Read-only access with proper enforcement
- ✅ Pattern matching and tree walking
- ✅ Configurable ClassLoader and base path

**Use for**:
- Application configuration
- Template management
- Static file serving
- Test fixtures
- Embedded documentation
- Resource cataloging

**Not for**:
- Write operations
- Dynamic resource discovery
- Watch service monitoring
- File handle access

ResourceFileSystem bridges the gap between classpath resources and filesystem APIs, making resource management elegant and type-safe!
