# ResourceFileSystem - Complete Working Example

This directory contains a complete working example of ResourceFileSystem with all necessary files.

## Directory Structure

```
test-resources/
├── config/
│   ├── resource.manifest
│   ├── app.properties
│   ├── database.properties
│   └── logging.xml
├── templates/
│   ├── resource.manifest
│   ├── email/
│   │   ├── welcome.html
│   │   └── goodbye.html
│   └── web/
│       ├── home.html
│       └── about.html
├── data/
│   ├── resource.manifest
│   ├── app.properties
│   └── data.txt
├── static/
│   ├── resource.manifest
│   ├── css/
│   │   ├── style.css
│   │   └── theme.css
│   ├── js/
│   │   ├── app.js
│   │   └── utils.js
│   └── images/
│       ├── logo.txt (placeholder)
│       └── banner.txt (placeholder)
└── docs/
    ├── resource.manifest
    ├── README.md
    └── LICENSE.txt
```

## Files Included

### 1. config/resource.manifest
Lists: app.properties, database.properties, logging.xml

### 2. templates/resource.manifest
Lists: email/welcome.html, email/goodbye.html, web/home.html, web/about.html

### 3. data/resource.manifest
Lists: app.properties, data.txt

### 4. static/resource.manifest
Lists: css/style.css, css/theme.css, js/app.js, js/utils.js, images/logo.txt, images/banner.txt

### 5. docs/resource.manifest
Lists: README.md, LICENSE.txt

## How to Use

### Option 1: Copy to src/main/resources

```bash
# Copy the entire test-resources directory
cp -r test-resources/* src/main/resources/
```

### Option 2: Copy to src/test/resources

```bash
# For testing purposes
cp -r test-resources/* src/test/resources/
```

### Option 3: Use with URLClassLoader

```java
// Point to the test-resources directory
Path resourceDir = Paths.get("test-resources");
URL[] urls = new URL[] { resourceDir.toUri().toURL() };
ClassLoader classLoader = new URLClassLoader(urls, null);

Map<String, Object> env = new HashMap<>();
env.put(ResourceFileSystemProvider.CLASSLOADER_PARAM, classLoader);
env.put(ResourceFileSystemProvider.BASE_PATH_PARAM, "config/");

FileSystem fs = provider.newFileSystem(URI.create("resource:///config"), env);
```

## Running the Demo

Once the resources are in place:

```java
public static void main(String[] args) {
    ResourceFileSystemDemo.main(args);
}
```

Or run individual demo methods:

```java
ResourceFileSystemDemo.demo1_BasicUsage();
ResourceFileSystemDemo.demo2_BrowseStructure();
ResourceFileSystemDemo.demo3_ReadFiles();
// etc.
```

## Creating Your Own Resources

1. Create a directory structure
2. Add your files
3. Generate manifest:

```bash
cd your-resource-dir
find . -type f ! -name 'resource.manifest' | sed 's|^./||' > resource.manifest
```

4. Use with ResourceFileSystem:

```java
Map<String, Object> env = Map.of(
    ResourceFileSystemProvider.BASE_PATH_PARAM, "your-resource-dir/"
);
FileSystem fs = provider.newFileSystem(uri, env);
```
