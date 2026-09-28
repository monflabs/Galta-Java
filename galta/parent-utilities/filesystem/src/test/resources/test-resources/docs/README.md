# ResourceFS Documentation

Welcome to ResourceFS - a Java NIO.2 FileSystem implementation for accessing classpath resources.

## Overview

ResourceFS provides a standard `FileSystem` interface for working with resources bundled in your application. Instead of using `ClassLoader.getResourceAsStream()`, you can use the familiar `java.nio.file.Files` API.

## Features

- **Standard API**: Use `Files.readAllBytes()`, `Files.walk()`, `Files.newInputStream()`, etc.
- **Directory Navigation**: Browse resource directories like a real filesystem
- **Pattern Matching**: Use glob and regex patterns to find resources
- **Manifest-Based**: Simple text file lists all available resources
- **Read-Only**: Resources are immutable and safe from modification

## Quick Start

### 1. Create a Manifest File

Create `resource.manifest` in your resources directory:

```
config/app.properties
templates/email.html
data/users.csv
```

### 2. Use ResourceFileSystem

```java
ResourceFileSystemProvider provider = new ResourceFileSystemProvider();

Map<String, Object> env = Map.of(
    ResourceFileSystemProvider.BASE_PATH_PARAM, "myapp/"
);

try (FileSystem fs = provider.newFileSystem(URI.create("resource:///myapp"), env)) {
    // Read a configuration file
    Path config = fs.getPath("/config/app.properties");
    Properties props = new Properties();
    try (InputStream in = Files.newInputStream(config)) {
        props.load(in);
    }
    
    // List all templates
    Path templates = fs.getPath("/templates");
    Files.list(templates).forEach(System.out::println);
    
    // Find all CSV files
    PathMatcher csvMatcher = fs.getPathMatcher("glob:**.csv");
    Files.walk(fs.getPath("/"))
        .filter(csvMatcher::matches)
        .forEach(System.out::println);
}
```

## Use Cases

- Application configuration files
- Email and web templates
- Static web assets (CSS, JS, images)
- Test fixtures and sample data
- Embedded documentation
- Localization files

## Configuration Options

- `classLoader`: Custom ClassLoader to use
- `basePath`: Base directory for resources
- `manifestFile`: Name of the manifest file (default: "resource.manifest")

## Manifest File Format

Simple text format, one file path per line:

```
# Comments start with #
file1.txt
dir1/file2.txt
dir1/subdir/file3.txt
```

Directories are automatically inferred from file paths.

## Generating Manifests

At build time, generate the manifest automatically:

```bash
cd src/main/resources/myapp
find . -type f ! -name 'resource.manifest' | sed 's|^./||' > resource.manifest
```

## Limitations

- Read-only (no write operations)
- Requires manifest file (cannot enumerate resources dynamically)
- No watch service support
- Cannot convert to `File` objects

## API Reference

See the JavaDoc for detailed API documentation.

## Examples

Check out the `ResourceFileSystemDemo` class for comprehensive examples.

## License

See LICENSE.txt for details.
