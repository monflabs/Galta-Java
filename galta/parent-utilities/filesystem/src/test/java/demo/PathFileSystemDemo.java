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
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.monflabs.filesystem.path.PathFileSystem;
import org.monflabs.filesystem.path.PathFileSystemProvider;
import org.monflabs.filesystem.path.PathPath;

/**
 * Comprehensive demonstration of PathFileSystem features.
 * 
 * PathFileSystem is a modern NIO.2 filesystem that:
 * - Always uses "/" as separator (even on Windows!)
 * - Uses Path instead of File
 * - Supports sandboxing to a directory
 * - Transparently translates paths to OS format
 */
public class PathFileSystemDemo {
    
    public static void main(String[] args) {
        try {
            System.out.println("=".repeat(60));
            System.out.println("PathFileSystem Demonstration");
            System.out.println("=".repeat(60));
            
            demo1_BasicCrossPlatform();
            demo2_Sandboxing();
            demo3_PathTranslation();
            demo4_DirectoryOperations();
            demo5_FileOperations();
            
            System.out.println("\n" + "=".repeat(60));
            System.out.println("All demonstrations completed successfully!");
            System.out.println("=".repeat(60));
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Demo 1: Cross-Platform Path Handling
     */
    private static void demo1_BasicCrossPlatform() throws IOException {
        System.out.println("\n=== Demo 1: Cross-Platform Path Handling ===");
        
        // Create filesystem with temporary sandbox
        Path tempDir = Files.createTempDirectory("pathfs-demo1");
        
        try (FileSystem fs = createPathFS(tempDir)) {
            System.out.println("Created PathFileSystem");
            System.out.println("  Root: " + tempDir);
            System.out.println("  Separator: '" + fs.getSeparator() + "' (always '/' on all OS)");
            System.out.println("  OS Separator: '" + FileSystems.getDefault().getSeparator() + "'");
            
            // Create file with "/" separator
            Path file = fs.getPath("/documents/readme.txt");
            Files.createDirectories(file.getParent());
            Files.writeString(file, "Hello from PathFileSystem!");
            
            System.out.println("\nCreated file:");
            System.out.println("  Virtual path: " + file);
            System.out.println("  Uses '/': " + file.toString().contains("/"));
            
            if (file instanceof PathPath) {
                PathPath pp = (PathPath) file;
                System.out.println("  OS path: " + pp.toOSPath());
            }
            
            // Read it back
            String content = Files.readString(file);
            System.out.println("  Content: " + content);
        } finally {
            deleteRecursively(tempDir);
        }
    }
    
    /**
     * Demo 2: Sandboxing
     */
    private static void demo2_Sandboxing() throws IOException {
        System.out.println("\n=== Demo 2: Sandboxing ===");
        
        // Create sandbox directory
        Path sandboxDir = Files.createTempDirectory("pathfs-sandbox");
        System.out.println("Sandbox root: " + sandboxDir);
        
        try (FileSystem fs = createPathFS(sandboxDir)) {
            // All operations confined to sandbox
            Path config = fs.getPath("/config/app.conf");
            Files.createDirectories(config.getParent());
            Files.writeString(config, "server=localhost\nport=8080");
            
            Path data = fs.getPath("/data/users.json");
            Files.createDirectories(data.getParent());
            Files.writeString(data, "{\"users\": []}");
            
            System.out.println("\nCreated files in sandbox:");
            Files.walk(fs.getPath("/"))
                .filter(Files::isRegularFile)
                .forEach(p -> System.out.println("  " + p));
            
            System.out.println("\nActual OS locations:");
            Files.walk(sandboxDir)
                .filter(Files::isRegularFile)
                .forEach(p -> System.out.println("  " + p));
            
        } finally {
            deleteRecursively(sandboxDir);
        }
    }
    
    /**
     * Demo 3: Path Translation
     */
    private static void demo3_PathTranslation() throws IOException {
        System.out.println("\n=== Demo 3: Path Translation ===");
        
        Path tempDir = Files.createTempDirectory("pathfs-demo3");
        
        try (FileSystem fs = createPathFS(tempDir)) {
            PathFileSystem pfs = (PathFileSystem) fs;
            
            // Show virtual to OS translation
            String[] virtualPaths = {
                "/",
                "/file.txt",
                "/dir/subdir/file.txt"
            };
            
            System.out.println("Virtual → OS path translation:");
            for (String virtualPath : virtualPaths) {
                java.nio.file.Path osPath = pfs.toOSPath(virtualPath);
                System.out.println("  '" + virtualPath + "' → '" + osPath + "'");
            }
            
            // Create some files to show OS to virtual translation
            Files.createDirectories(tempDir.resolve("test").resolve("data"));
            java.nio.file.Path osFile = tempDir.resolve("test").resolve("data").resolve("file.txt");
            Files.writeString(osFile, "test");
            
            System.out.println("\nOS → Virtual path translation:");
            System.out.println("  '" + osFile + "'");
            System.out.println("  → '" + pfs.toVirtualPath(osFile) + "'");
            
        } finally {
            deleteRecursively(tempDir);
        }
    }
    
    /**
     * Demo 4: Directory Operations
     */
    private static void demo4_DirectoryOperations() throws IOException {
        System.out.println("\n=== Demo 4: Directory Operations ===");
        
        Path tempDir = Files.createTempDirectory("pathfs-demo4");
        
        try (FileSystem fs = createPathFS(tempDir)) {
            // Create nested directories (always with /)
            Path deepDir = fs.getPath("/level1/level2/level3");
            Files.createDirectories(deepDir);
            System.out.println("Created nested directories: " + deepDir);
            
            // Create files at different levels
            Files.writeString(fs.getPath("/level1/file1.txt"), "Level 1");
            Files.writeString(fs.getPath("/level1/level2/file2.txt"), "Level 2");
            Files.writeString(fs.getPath("/level1/level2/level3/file3.txt"), "Level 3");
            
            // Walk directory tree
            System.out.println("\nDirectory tree:");
            Files.walk(fs.getPath("/"))
                .forEach(p -> {
                    int depth = p.getNameCount();
                    String indent = "  ".repeat(depth);
                    String name = depth == 0 ? "/" : p.getFileName().toString();
                    String type = Files.isDirectory(p) ? "/" : "";
                    System.out.println(indent + name + type);
                });
            
            // List specific directory
            System.out.println("\nContents of /level1/level2:");
            try (DirectoryStream<Path> stream = 
                    Files.newDirectoryStream(fs.getPath("/level1/level2"))) {
                for (Path entry : stream) {
                    System.out.println("  " + entry.getFileName());
                }
            }
            
        } finally {
            deleteRecursively(tempDir);
        }
    }
    
    /**
     * Demo 5: File Operations
     */
    private static void demo5_FileOperations() throws IOException {
        System.out.println("\n=== Demo 5: File Operations ===");
        
        Path tempDir = Files.createTempDirectory("pathfs-demo5");
        
        try (FileSystem fs = createPathFS(tempDir)) {
            // Create
            Path file = fs.getPath("/test.txt");
            Files.writeString(file, "Original content");
            System.out.println("Created: " + file);
            System.out.println("  Size: " + Files.size(file) + " bytes");
            
            // Copy
            Path copy = fs.getPath("/test-copy.txt");
            Files.copy(file, copy);
            System.out.println("\nCopied to: " + copy);
            System.out.println("  Content: " + Files.readString(copy));
            
            // Move/Rename
            Path moved = fs.getPath("/renamed.txt");
            Files.move(copy, moved);
            System.out.println("\nMoved to: " + moved);
            System.out.println("  Original exists: " + Files.exists(copy));
            System.out.println("  New exists: " + Files.exists(moved));
            
            // Modify
            Files.writeString(file, "Modified content");
            System.out.println("\nModified: " + file);
            System.out.println("  New content: " + Files.readString(file));
            
            // Delete
            Files.delete(moved);
            System.out.println("\nDeleted: " + moved);
            System.out.println("  Exists: " + Files.exists(moved));
            
            // Attributes
            var attrs = Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes.class);
            System.out.println("\nFile attributes:");
            System.out.println("  Size: " + attrs.size() + " bytes");
            System.out.println("  Created: " + attrs.creationTime());
            System.out.println("  Modified: " + attrs.lastModifiedTime());
            System.out.println("  Is directory: " + attrs.isDirectory());
            System.out.println("  Is regular file: " + attrs.isRegularFile());
            
        } finally {
            deleteRecursively(tempDir);
        }
    }
    
    /**
     * Helper to create PathFileSystem with optional root.
     */
    private static FileSystem createPathFS(Path rootPath) throws IOException {
        Map<String, Object> env = new HashMap<>();
        if (rootPath != null) {
            env.put(PathFileSystemProvider.ROOT_PARAM, rootPath);
        }
        
        PathFileSystemProvider provider = PathFileSystem.DEFAUT_PROVIDER;
        return provider.newFileSystem(
            URI.create("pathfs:///" + UUID.randomUUID()), 
            env
        );
    }
    
    /**
     * Recursively delete a directory.
     */
    private static void deleteRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        // Ignore
                    }
                });
        }
    }
}
