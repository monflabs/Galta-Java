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
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.ReadOnlyFileSystemException;
import java.util.HashMap;
import java.util.Map;

import org.monflabs.filesystem.zip.ZipFileSystem;
import org.monflabs.tests.UnitTestSupport;

/**
 * Demonstration of ZipFileSystem - read-only access to ZIP files.
 */
public class ZipFileSystemDemo {
	
	private static UnitTestSupport support = new UnitTestSupport(ZipFileSystemDemo.class);
	static final Path zipPath = (new File(support.getTargetTempDirectory("zip",true),"demo.zip")).toPath(); 

    public static void main(String[] args) {
        System.out.println("=== ZipFileSystem Demo ===\n");
        
        try {
            // Demo 1: Create a sample ZIP file
            demo1_createSampleZip();
            
            // Demo 2: Open and browse ZIP
            demo2_openAndBrowseZip();
            
            // Demo 3: Read file contents
            demo3_readFileContents();
            
            // Demo 4: Directory walking
            demo4_walkZipTree();
            
            // Demo 5: Pattern matching
            demo5_patternMatching();
            
            // Demo 6: Read-only enforcement
            demo6_readOnlyEnforcement();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Demo 1: Create a sample ZIP file to work with.
     */
    private static void demo1_createSampleZip() throws IOException {
        System.out.println("--- Demo 1: Creating Sample ZIP ---");

        // Create ZIP with default JAR filesystem provider
        Map<String, String> env = new HashMap<>();
        env.put("create", "true");
        URI uri = URI.create("jar:" + zipPath.toUri());
        try (FileSystem zipFs = FileSystems.newFileSystem(uri, env)) {
            Files.write(zipFs.getPath("/README.md"), "# Demo ZIP File\n\nThis is a sample.".getBytes());
            
            Files.createDirectories(zipFs.getPath("/docs"));
            Files.write(zipFs.getPath("/docs/manual.txt"), "User Manual\n============\n\nInstructions here.".getBytes());
            Files.write(zipFs.getPath("/docs/faq.txt"), "FAQ\n===\n\nQ: How?\nA: Like this.".getBytes());
            
            Files.createDirectories(zipFs.getPath("/src/main"));
            Files.write(zipFs.getPath("/src/main/App.java"), "public class App {\n    public static void main(String[] args) {}\n}".getBytes());
            
            Files.createDirectories(zipFs.getPath("/src/test"));
            Files.write(zipFs.getPath("/src/test/AppTest.java"), "public class AppTest {\n    // Tests here\n}".getBytes());
        }
        
        System.out.println("Created demo.zip with sample content");
        System.out.println("ZIP file size: " + Files.size(zipPath) + " bytes\n");
    }
    
    /**
     * Demo 2: Open ZIP and browse contents.
     */
    private static void demo2_openAndBrowseZip() throws IOException {
        System.out.println("--- Demo 2: Opening and Browsing ZIP ---");
        
        try (FileSystem fs = ZipFileSystem.newBuilder().zipFile(zipPath).build()) {
            System.out.println("Filesystem: " + fs);
            System.out.println("Provider: " + fs.provider().getScheme());
            System.out.println("Read-only: " + fs.isReadOnly());
            System.out.println("Separator: " + fs.getSeparator());
            
            // List root directory
            System.out.println("\nRoot directory contents:");
            Path root = fs.getPath(fs.getSeparator());
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
                for (Path entry : stream) {
                    boolean isDir = Files.isDirectory(entry);
                    long size = isDir ? 0 : Files.size(entry);
                    System.out.println("  " + entry.getFileName() + 
                                     (isDir ? " [DIR]" : " (" + size + " bytes)"));
                }
            }
            
            // List /docs directory
            System.out.println("\n/docs directory contents:");
            Path docs = fs.getPath(fs.getSeparator()+"docs");
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(docs)) {
                for (Path entry : stream) {
                    System.out.println("  " + entry.getFileName() + " (" + Files.size(entry) + " bytes)");
                }
            }
        }
        
        System.out.println();
    }
    
    /**
     * Demo 3: Read file contents from ZIP.
     */
    private static void demo3_readFileContents() throws IOException {
        System.out.println("--- Demo 3: Reading File Contents ---");
        
        try (FileSystem fs = ZipFileSystem.newBuilder().zipFile(zipPath).build()) {
            // Read README.md
            Path readme = fs.getPath("/README.md");
            String content = new String(Files.readAllBytes(readme));
            System.out.println("README.md:");
            System.out.println(content);
            
            // Read App.java
            System.out.println("\nApp.java:");
            Path app = fs.getPath("/src/main/App.java");
            content = new String(Files.readAllBytes(app));
            System.out.println(content);
        }
        
        System.out.println();
    }
    
    /**
     * Demo 4: Walk entire ZIP tree.
     */
    private static void demo4_walkZipTree() throws IOException {
        System.out.println("--- Demo 4: Walking ZIP Tree ---");
        
        try (FileSystem fs = ZipFileSystem.newBuilder().zipFile(zipPath).build()) {
            Path root = fs.getPath(fs.getSeparator());
            
            System.out.println("Complete ZIP structure:");
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
        }
        
        System.out.println();
    }
    
    /**
     * Demo 5: Pattern matching in ZIP.
     */
    private static void demo5_patternMatching() throws IOException {
        System.out.println("--- Demo 5: Pattern Matching ---");
        
        try (FileSystem fs = ZipFileSystem.newBuilder().zipFile(zipPath).build()) {
            // Find all .java files
            PathMatcher javaMatcher = fs.getPathMatcher("glob:**/*.java");
            
            System.out.println("All .java files:");
            Files.walk(fs.getPath(fs.getSeparator()))
                .filter(javaMatcher::matches)
                .forEach(path -> System.out.println("  " + path));
            
            // Find all .txt files
            PathMatcher txtMatcher = fs.getPathMatcher("glob:**/*.txt");
            
            System.out.println("\nAll .txt files:");
            Files.walk(fs.getPath(fs.getSeparator()))
                .filter(txtMatcher::matches)
                .forEach(path -> System.out.println("  " + path));
        }
        
        System.out.println();
    }
    
    /**
     * Demo 6: Demonstrate read-only enforcement.
     */
    private static void demo6_readOnlyEnforcement() throws IOException {
        System.out.println("--- Demo 6: Read-Only Enforcement ---");
        
        try (FileSystem fs = ZipFileSystem.newBuilder().zipFile(zipPath).build()) {
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
                Path readme = fs.getPath("/README.md");
                Files.delete(readme);
                System.out.println("ERROR: Delete should have failed!");
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
                Path readme = fs.getPath("/README.md");
                byte[] content = Files.readAllBytes(readme);
                System.out.println("✓ Read operation allowed: read " + content.length + " bytes");
            } catch (Exception e) {
                System.out.println("ERROR: Read should have succeeded!");
            }
        }
        
        System.out.println();
    }
}
