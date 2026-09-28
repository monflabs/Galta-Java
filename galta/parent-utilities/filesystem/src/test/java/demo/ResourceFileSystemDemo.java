/*
 * Copyright (c) 2019-2026 Philippe Riand
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package demo;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.ReadOnlyFileSystemException;
import java.util.HashMap;
import java.util.Map;

import org.monflabs.filesystem.resources.ResourceFileSystem;

/**
 * Demonstration of ResourceFileSystem with actual resource files.
 * 
 * To run this demo:
 * 1. Copy the test-resources directory to your project
 * 2. Update the RESOURCE_DIR path below to point to test-resources
 * 3. Run the demo
 */
public class ResourceFileSystemDemo {
	
    public static void main(String[] args) {
        System.out.println("=== ResourceFileSystem Working Demo ===\n");
        
        try {
            // Demo 1: Configuration files
            demo1_ConfigurationFiles();
            
            // Demo 2: Email templates
            demo2_EmailTemplates();
            
            // Demo 3: Web templates
            demo3_WebTemplates();
            
            // Demo 4: Static assets
            demo4_StaticAssets();
            
            // Demo 5: Documentation
            demo5_Documentation();
            
            // Demo 6: Pattern matching
            demo6_PatternMatching();
            
            // Demo 7: Tree Walking
            demo7_WalkTree();
            
            // Demo 8: Read Only FS
            demo8_ReadOnlyEnforcement();
            
        } catch (Exception e) {
            System.err.println("Error running demo: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Create a ResourceFileSystem pointing to the test resources.
     * Uses a single global resources.manifest at the root.
     */
    private static FileSystem openResourceFS() throws IOException {
        return  ResourceFileSystem.newBuilder()
        			.classLoader(ResourceFileSystemDemo.class.getClassLoader())
        			.root("test-resources")
        			.build();
    }
    
    /**
     * Demo 1: Load configuration files.
     */
    private static void demo1_ConfigurationFiles() throws IOException {
        System.out.println("--- Demo 1: Configuration Files ---");
        
        try (FileSystem fs = openResourceFS()) {
            // List all config files
            System.out.println("Configuration files:");
            Path configDir = fs.getPath("/config");
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDir)) {
                for (Path entry : stream) {
                    if (Files.isRegularFile(entry)) {
                        long size = Files.size(entry);
                        System.out.println("  " + entry.getFileName() + " (" + size + " bytes)");
                    }
                }
            }
            
            // Read app.properties
            System.out.println("\nconfig/app.properties content:");
            Path appProps = fs.getPath("/config/app.properties");
            String content = Files.readString(appProps);
            System.out.println(content);
        }
        
        System.out.println();
    }
    
    /**
     * Demo 2: Load email templates.
     */
    private static void demo2_EmailTemplates() throws IOException {
        System.out.println("--- Demo 2: Email Templates ---");
        
        try (FileSystem fs = openResourceFS()) {
            // List email templates
            System.out.println("Email templates:");
            Path emailDir = fs.getPath("/templates/email");
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(emailDir)) {
                for (Path entry : stream) {
                    System.out.println("  " + entry.getFileName() + 
                                     " (" + Files.size(entry) + " bytes)");
                }
            }
            
            // Read welcome email
            System.out.println("\nWelcome email (first 200 chars):");
            Path welcome = fs.getPath("/templates/email/welcome.html");
            String content = Files.readString(welcome);
            System.out.println(content.substring(0, Math.min(200, content.length())) + "...");
        }
        
        System.out.println();
    }
    
    /**
     * Demo 3: Load web templates.
     */
    private static void demo3_WebTemplates() throws IOException {
        System.out.println("--- Demo 3: Web Templates ---");
        
        try (FileSystem fs = openResourceFS()) {
            // List web templates
            System.out.println("Web templates:");
            Path webDir = fs.getPath("/templates/web");
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(webDir)) {
                for (Path entry : stream) {
                    System.out.println("  " + entry.getFileName() + 
                                     " (" + Files.size(entry) + " bytes)");
                }
            }
            
            // Show structure
            System.out.println("\nTemplate directory structure:");
            Path templatesDir = fs.getPath("/templates");
            Files.walk(templatesDir)
                .forEach(path -> {
                    int depth = path.getNameCount() - templatesDir.getNameCount();
                    String indent = "  ".repeat(depth);
                    String name = depth == 0 ? "templates/" : path.getFileName().toString();
                    String type = Files.isDirectory(path) ? "[DIR]" : "";
                    System.out.println(indent + name + " " + type);
                });
        }
        
        System.out.println();
    }
    
    /**
     * Demo 4: Load static assets (CSS, JS).
     */
    private static void demo4_StaticAssets() throws IOException {
        System.out.println("--- Demo 4: Static Assets ---");
        
        try (FileSystem fs = openResourceFS()) {
            // Count files by type
            Map<String, Integer> counts = new HashMap<>();
            
            Path staticDir = fs.getPath("/static");
            Files.walk(staticDir)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    String name = path.getFileName().toString();
                    String ext = name.contains(".") ? 
                        name.substring(name.lastIndexOf(".")) : "no-ext";
                    counts.merge(ext, 1, Integer::sum);
                });
            
            System.out.println("Asset counts by type:");
            counts.forEach((ext, count) -> 
                System.out.println("  " + ext + ": " + count + " file(s)"));
            
            // Show CSS content
            System.out.println("\nstyle.css (first 300 chars):");
            Path style = fs.getPath("/static/css/style.css");
            String content = Files.readString(style);
            System.out.println(content.substring(0, Math.min(300, content.length())) + "...");
        }
        
        System.out.println();
    }
    
    /**
     * Demo 5: Read documentation.
     */
    private static void demo5_Documentation() throws IOException {
        System.out.println("--- Demo 5: Documentation ---");
        
        try (FileSystem fs = openResourceFS()) {
            // List docs
            System.out.println("Documentation files:");
            Path docsDir = fs.getPath("/docs");
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(docsDir)) {
                for (Path entry : stream) {
                    if (Files.isRegularFile(entry)) {
                        System.out.println("  " + entry.getFileName() + 
                                         " (" + Files.size(entry) + " bytes)");
                    }
                }
            }
            
            // Show README excerpt
            System.out.println("\nREADME.md excerpt:");
            Path readme = fs.getPath("/docs/README.md");
            String content = Files.readString(readme);
            String[] lines = content.split("\n");
            for (int i = 0; i < Math.min(10, lines.length); i++) {
                System.out.println(lines[i]);
            }
            System.out.println("...");
        }
        
        System.out.println();
    }
    
    /**
     * Demo 6: Pattern matching across all resources.
     */
    private static void demo6_PatternMatching() throws IOException {
        System.out.println("--- Demo 6: Pattern Matching ---");
        
        try (FileSystem fs = openResourceFS()) {
            // Find all CSS files
            System.out.println("All CSS files:");
            PathMatcher cssMatcher = fs.getPathMatcher("glob:**.css");
            Files.walk(fs.getPath(fs.getSeparator()))
                .filter(Files::isRegularFile)
                .filter(cssMatcher::matches)
                .forEach(path -> System.out.println("  " + path));
            
            // Find all JS files
            System.out.println("\nAll JavaScript files:");
            PathMatcher jsMatcher = fs.getPathMatcher("glob:**.js");
            Files.walk(fs.getPath(fs.getSeparator()))
                .filter(Files::isRegularFile)
                .filter(jsMatcher::matches)
                .forEach(path -> System.out.println("  " + path));
            
            // Find files in images directory
            System.out.println("\nAll files in images directory:");
            PathMatcher imagesMatcher = fs.getPathMatcher("glob:static/images/*");
            Files.walk(fs.getPath(fs.getSeparator()))
                .filter(imagesMatcher::matches)
                .forEach(path -> System.out.println("  " + path));
            
            // Find all HTML files
            System.out.println("\nAll HTML templates:");
            PathMatcher htmlMatcher = fs.getPathMatcher("glob:templates/**/*.html");
            Files.walk(fs.getPath(fs.getSeparator()))
                .filter(htmlMatcher::matches)
                .forEach(path -> System.out.println("  " + path));
        }
        
        System.out.println();
    }
    
    /**
     * Demo 7: Walk entire resource tree.
     */
    private static void demo7_WalkTree() throws IOException {
        System.out.println("--- Demo 7: Walking Resource Tree ---");
        
        try (FileSystem fs = openResourceFS()) {
            Path root = fs.getPath(fs.getSeparator());
            
            System.out.println("Complete resource structure:");
            Files.walk(root).forEach(path -> {
                try {
                    int depth = path.getNameCount();
                    String indent = "  ".repeat(depth);
                    String name = depth == 0 ? "/" : path.getFileName().toString();
                    
                    if (Files.isDirectory(path)) {
                        System.out.println(indent + name + "/");
                    } else {
                        long size = Files.size(path);
                        System.out.println(indent + name + " (" + size + " bytes)");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            
            // Count files by extension
            Map<String, Long> extensionCounts = new HashMap<>();
            Files.walk(root)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    String name = path.getFileName().toString();
                    int dotIndex = name.lastIndexOf('.');
                    String extension = dotIndex > 0 ? name.substring(dotIndex) : "(no extension)";
                    extensionCounts.merge(extension, 1L, Long::sum);
                });
            
            System.out.println("\nFile count by extension:");
            extensionCounts.forEach((ext, count) -> 
                System.out.println("  " + ext + ": " + count));
        }
        
        System.out.println();
    }
    
    /**
     * Demo 8: Demonstrate read-only enforcement.
     */
    private static void demo8_ReadOnlyEnforcement() throws IOException {
        System.out.println("--- Demo 8: Read-Only Enforcement ---");
        
        try (FileSystem fs = openResourceFS()) {
            Path testFile = fs.getPath("/new-file.txt");
            
            // Try to write - should fail
            try {
                Files.write(testFile, "This should fail".getBytes());
                System.out.println("ERROR: Write should have failed!");
            } catch (ReadOnlyFileSystemException e) {
                System.out.println("✓ Write operation correctly blocked: " + e.getClass().getSimpleName());
            }
            
            // Try to delete - should fail
            try {
                Path readme = fs.getPath("/docs/README.md");
                if (Files.exists(readme)) {
                    Files.delete(readme);
                    System.out.println("ERROR: Delete should have failed!");
                }
            } catch (ReadOnlyFileSystemException e) {
                System.out.println("✓ Delete operation correctly blocked: " + e.getClass().getSimpleName());
            }
            
            // Try to create directory - should fail
            try {
                Path newDir = fs.getPath("/new-directory");
                Files.createDirectory(newDir);
                System.out.println("ERROR: Create directory should have failed!");
            } catch (ReadOnlyFileSystemException e) {
                System.out.println("✓ Create directory correctly blocked: " + e.getClass().getSimpleName());
            }
            
            // Reading is allowed
            try {
                Path readme = fs.getPath("/docs/README.md");
                if (Files.exists(readme)) {
                    byte[] content = Files.readAllBytes(readme);
                    System.out.println("✓ Read operation allowed: read " + content.length + " bytes");
                } else {
                    System.out.println("✓ Read operation allowed (file would be readable if it existed)");
                }
            } catch (Exception e) {
                System.out.println("Note: Could not test read operation");
            }
        }
        
        System.out.println();
    }
}
