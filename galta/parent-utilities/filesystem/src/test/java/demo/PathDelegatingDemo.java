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
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import org.monflabs.filesystem.delegate.PathDelegatingFileSystem;
import org.monflabs.filesystem.delegate.PathDelegatingPath;
import org.monflabs.filesystem.memory.MemoryFileSystem;
import org.monflabs.tests.UnitTestSupport;

/**
 * Demo showing the Path-delegating filesystem with various underlying filesystems.
 */
public class PathDelegatingDemo {
	
	private static UnitTestSupport support = new UnitTestSupport(ZipFileSystemDemo.class);
	static final Path fsPath = (support.getTargetTempDirectory("delegate",true)).toPath(); 

    public static void main(String[] args) {
        System.out.println("=== Path-Delegating FileSystem Demo ===\n");
        
        try {
            // Demo 1: Delegate to a File-based path
            demonstrateDelegateToFile();
            
            System.out.println("\n" + "=".repeat(60) + "\n");
            
            // Demo 2: Delegate to a Memory-based path
            demonstrateDelegateToMemory();
            
            System.out.println("\n" + "=".repeat(60) + "\n");
            
            // Demo 3: Nested delegation (delegate to a delegate!)
            demonstrateNestedDelegation();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void demonstrateDelegateToFile() throws IOException {
        System.out.println("### Demo 1: Delegating to File-based Path ###\n");
        
        // Create a directory structure on the real filesystem
        Path realRoot = fsPath; 
        System.out.println("Created real directory: " + realRoot);
        
        // Create some structure
        Files.createDirectories(realRoot.resolve("subdir1"));
        Files.createDirectories(realRoot.resolve("subdir2"));
        Files.write(realRoot.resolve("root-file.txt"), "Root level file".getBytes());
        Files.write(realRoot.resolve("subdir1/file1.txt"), "File in subdir1".getBytes());
        
        // Create delegating filesystem
        try (FileSystem fs = PathDelegatingFileSystem.newBuilder().root(realRoot).build()) {
            System.out.println("Created delegating filesystem");
            System.out.println("Root: " + ((PathDelegatingFileSystem) fs).getRootPath());
            System.out.println();
            
            // List root directory
            System.out.println("Root directory contents:");
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(fs.getPath(fs.getSeparator()))) {
                for (Path entry : stream) {
                    String type = Files.isDirectory(entry) ? "[DIR]" : "[FILE]";
                    System.out.println("  " + entry + " " + type);
                }
            }
            System.out.println();
            
            // Read a file
            Path file = fs.getPath("/root-file.txt");
            String content = new String(Files.readAllBytes(file));
            System.out.println("Read file content: " + content);
            System.out.println();
            
            // Create a new file in the delegate
            Path newFile = fs.getPath("/subdir2/new-file.txt");
            Files.write(newFile, "Created via delegate!".getBytes());
            System.out.println("Created new file: " + newFile);
            
            // Verify it exists in the real filesystem
            Path realFile = realRoot.resolve("subdir2/new-file.txt");
            System.out.println("Exists in real filesystem: " + Files.exists(realFile));
            System.out.println("Real path: " + realFile);
            System.out.println();
            
            // Try to escape (should fail)
            System.out.println("Security test - attempting to escape:");
            try {
                Path escape = fs.getPath("/../../../etc/passwd");
                Files.readAllBytes(escape);
                System.out.println("  ⚠ WARNING: Escape was possible!");
            } catch (AccessDeniedException e) {
                System.out.println("  ✓ Access denied: " + e.getMessage());
            } catch (IOException e) {
                System.out.println("  ✓ I/O error (sandbox working): " + e.getMessage());
            }
        }
        
        // Cleanup
        deleteRecursively(realRoot);
        System.out.println("\nCleaned up real directory");
    }
    
    private static void demonstrateDelegateToMemory() throws IOException {
        System.out.println("### Demo 2: Delegating to Memory FileSystem ###\n");
        
        // Create a memory filesystem
        FileSystem memFs = MemoryFileSystem.newBuilder().build();
        
        // Create structure in memory filesystem
        Path memRoot = memFs.getPath("/app-data");
        Files.createDirectories(memRoot);
        Files.createDirectories(memRoot.resolve("configs"));
        Files.createDirectories(memRoot.resolve("data"));
        Files.write(memRoot.resolve("configs/app.conf"), "setting=value".getBytes());
        Files.write(memRoot.resolve("data/file.dat"), "data content".getBytes());
        
        System.out.println("Created memory filesystem structure at: " + memRoot);
        System.out.println();
        
        try (FileSystem delFs = PathDelegatingFileSystem.newBuilder().root(memRoot).build()) {
            System.out.println("Created delegating filesystem over memory filesystem");
            System.out.println("Delegate info: " + ((PathDelegatingFileSystem) delFs).getDelegateInfo());
            System.out.println();
            
            // From the delegate's perspective, "/" is /app-data in memory
            System.out.println("Root directory (delegate view):");
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(delFs.getPath(delFs.getSeparator()))) {
                for (Path entry : stream) {
                    String type = Files.isDirectory(entry) ? "[DIR]" : "[FILE]";
                    System.out.println("  " + entry + " " + type);
                }
            }
            System.out.println();
            
            // Read config file
            Path config = delFs.getPath("/configs/app.conf");
            String content = new String(Files.readAllBytes(config));
            System.out.println("Read config: " + content);
            System.out.println();
            
            // Create new file
            Path newFile = delFs.getPath("/data/new.dat");
            Files.write(newFile, "new data".getBytes());
            System.out.println("Created file in delegate: " + newFile);
            
            // Verify in memory filesystem
            Path memFile = memRoot.resolve("data/new.dat");
            System.out.println("Exists in memory filesystem: " + Files.exists(memFile));
            System.out.println("Memory path: " + memFile);
            System.out.println();
            
            // Show that we can't access parent directory
            System.out.println("Security test:");
            try {
                Path parent = delFs.getPath("/..");
                Files.exists(parent.toRealPath());
                System.out.println("  ⚠ WARNING: Could access parent!");
            } catch (IOException e) {
                System.out.println("  ✓ Cannot escape to parent: " + e.getMessage());
            }
        }
        
        memFs.close();
        System.out.println("\nClosed memory filesystem");
    }
    
    private static void demonstrateNestedDelegation() throws IOException {
        System.out.println("### Demo 3: Nested Delegation ###\n");
        System.out.println("Creating a delegate of a delegate!\n");
        
        // Level 1: Real filesystem
        Path realRoot = Files.createTempDirectory("nested-delegate");
        Files.createDirectories(realRoot.resolve("level1/level2/level3"));
        Files.write(realRoot.resolve("level1/file1.txt"), "Level 1".getBytes());
        Files.write(realRoot.resolve("level1/level2/file2.txt"), "Level 2".getBytes());
        Files.write(realRoot.resolve("level1/level2/level3/file3.txt"), "Level 3".getBytes());
        
        System.out.println("Level 0 (Real FS): " + realRoot);
        
        // Level 2: First delegate to /level1
        FileSystem fs1 = PathDelegatingFileSystem.newBuilder().root(realRoot.resolve("level1")).build();
        System.out.println("Level 1 (Delegate): " + ((PathDelegatingFileSystem) fs1).getRootPath());
        
        // Level 3: Second delegate to /level2 within first delegate
        Path level2InDelegate = fs1.getPath("/level2");
        PathDelegatingPath delPath = (PathDelegatingPath) level2InDelegate;
        Path level2Real = delPath.toDelegatePath();
        
        try (FileSystem fs2 = PathDelegatingFileSystem.newBuilder().root(level2Real).build();) {
            System.out.println("Level 2 (Nested Delegate): " + ((PathDelegatingFileSystem) fs2).getRootPath());
            System.out.println();
            
            // Now fs2's "/" maps to .../level1/level2 in the real filesystem
            System.out.println("Contents of nested delegate root:");
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(fs2.getPath(fs2.getSeparator()))) {
                for (Path entry : stream) {
                    String type = Files.isDirectory(entry) ? "[DIR]" : "[FILE]";
                    System.out.println("  " + entry + " " + type);
                }
            }
            System.out.println();
            
            // Read file from nested delegate
            Path file = fs2.getPath("/file2.txt");
            String content = new String(Files.readAllBytes(file));
            System.out.println("Read from nested delegate: " + content);
            System.out.println();
            
            // Access subdirectory
            Path level3 = fs2.getPath("/level3/file3.txt");
            content = new String(Files.readAllBytes(level3));
            System.out.println("Read from subdirectory: " + content);
            System.out.println();
            
            // Show the path relationships
            System.out.println("Path relationships:");
            Path testPath = fs2.getPath("/level3/file3.txt");
            System.out.println("  In nested delegate: " + testPath);
            Path delegatePath = ((PathDelegatingPath) testPath).toDelegatePath();
            System.out.println("  In real filesystem: " + delegatePath);
            System.out.println("  Absolute real path: " + delegatePath.toAbsolutePath());
        }
        
        fs1.close();
        deleteRecursively(realRoot);
        System.out.println("\nCleaned up");
    }
    
    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path entry : stream) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}
