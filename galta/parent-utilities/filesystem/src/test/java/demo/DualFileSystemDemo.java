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
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.HashMap;
import java.util.Map;

import org.monflabs.filesystem.AbstractFileSystemProvider;
import org.monflabs.filesystem.file.FileFileSystem;
import org.monflabs.filesystem.file.FileFileSystemProvider;
import org.monflabs.filesystem.memory.MemoryFileSystem;
import org.monflabs.filesystem.memory.MemoryFileSystemProvider;
import org.monflabs.tests.UnitTestSupport;

/**
 * Comprehensive demo showing both File-based and Memory-based FileSystem implementations.
 * Demonstrates that both share common base classes and behave similarly.
 */
public class DualFileSystemDemo {

	private static UnitTestSupport support = new UnitTestSupport(ZipFileSystemDemo.class);
	static final Path fsPath = (support.getTargetTempDirectory("file",true)).toPath(); 

    public static void main(String[] args) {
        System.out.println("=== Dual FileSystem Implementation Demo ===\n");
        System.out.println("This demo shows two FileSystem implementations:");
        System.out.println("1. File-based: Delegates to java.io.File API");
        System.out.println("2. Memory-based: Everything stored in memory");
        System.out.println("Both share common base classes (Abstract*)\n");
        System.out.println("=".repeat(60) + "\n");
        
        try {
            demonstrateFilesystem("File-based", FileFileSystem.DEFAUT_PROVIDER, FileFileSystemProvider.SCHEME);
            System.out.println("\n" + "=".repeat(60) + "\n");
            demonstrateFilesystem("Memory-based", MemoryFileSystem.DEFAUT_PROVIDER, MemoryFileSystemProvider.SCHEME);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void demonstrateFilesystem(String name, AbstractFileSystemProvider provider, 
                                             String scheme) throws IOException {
        System.out.println("### " + name + " FileSystem ###\n");
        
        URI uri = URI.create(scheme + ":///");
        Map<String, Object> env = new HashMap<>();
        
        try (FileSystem fs = provider.newFileSystem(uri, env)) {
            System.out.println("1. FileSystem Properties:");
            System.out.println("   Provider: " + provider.getClass().getSimpleName());
            System.out.println("   Scheme: " + provider.getScheme());
            System.out.println("   Separator: " + fs.getSeparator());
            System.out.println("   Open: " + fs.isOpen());
            System.out.println("   Read-only: " + fs.isReadOnly());
            System.out.println();
            
            // Create test directory
            Path testDir;
            if (provider instanceof FileFileSystemProvider) {
                // Use temp directory for file-based
                testDir = fsPath;
            } else {
                // Use root for memory-based
                testDir = fs.getPath("/test");
            }
            
            // Clean up if exists
            if (Files.exists(testDir)) {
                deleteRecursively(testDir);
            }
            
            Files.createDirectory(testDir);
            System.out.println("2. Created test directory: " + testDir);
            System.out.println();
            
            // Create files
            Path file1 = testDir.resolve("file1.txt");
            Path file2 = testDir.resolve("file2.txt");
            Path subDir = testDir.resolve("subdir");
            
            Files.write(file1, "Hello from file 1!".getBytes());
            Files.write(file2, "Hello from file 2!".getBytes());
            Files.createDirectory(subDir);
            
            Path file3 = subDir.resolve("file3.txt");
            Files.write(file3, "Hello from subdirectory!".getBytes());
            
            System.out.println("3. Created files:");
            System.out.println("   " + file1 + " (" + Files.size(file1) + " bytes)");
            System.out.println("   " + file2 + " (" + Files.size(file2) + " bytes)");
            System.out.println("   " + file3 + " (" + Files.size(file3) + " bytes)");
            System.out.println();
            
            // List directory contents
            System.out.println("4. Directory listing:");
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(testDir)) {
                for (Path entry : stream) {
                    String type = Files.isDirectory(entry) ? "[DIR]" : "[FILE]";
                    System.out.println("   " + entry.getFileName() + " " + type);
                }
            }
            System.out.println();
            
            // Read file
            String content = new String(Files.readAllBytes(file1));
            System.out.println("5. Read file content:");
            System.out.println("   " + content);
            System.out.println();
            
            // Copy file
            Path copiedFile = testDir.resolve("file1-copy.txt");
            Files.copy(file1, copiedFile);
            System.out.println("6. Copied file: " + file1.getFileName() + " -> " + copiedFile.getFileName());
            System.out.println();
            
            // Path operations
            System.out.println("7. Path operations:");
            Path complexPath = testDir.resolve("a/b/../c/./d");
            System.out.println("   Original: " + complexPath);
            System.out.println("   Normalized: " + complexPath.normalize());
            System.out.println("   Parent: " + file1.getParent());
            System.out.println("   Filename: " + file1.getFileName());
            System.out.println("   Is absolute: " + file1.isAbsolute());
            System.out.println();
            
            // PathMatcher
            System.out.println("8. PathMatcher (glob):");
            PathMatcher matcher = fs.getPathMatcher("glob:*.txt");
            System.out.println("   Pattern: *.txt");
            System.out.println("   Matches 'file1.txt': " + matcher.matches(file1.getFileName()));
            System.out.println("   Matches 'subdir': " + matcher.matches(subDir.getFileName()));
            System.out.println();
            
            // File attributes
            System.out.println("9. File attributes:");
            System.out.println("   Exists: " + Files.exists(file1));
            System.out.println("   Is regular file: " + Files.isRegularFile(file1));
            System.out.println("   Is directory: " + Files.isDirectory(file1));
            System.out.println("   Readable: " + Files.isReadable(file1));
            System.out.println("   Writable: " + Files.isWritable(file1));
            System.out.println();
            
            // FileStore
            System.out.println("10. FileStore information:");
            FileStore store = Files.getFileStore(testDir);
            System.out.println("   Name: " + store.name());
            System.out.println("   Type: " + store.type());
            System.out.println("   Total space: " + formatBytes(store.getTotalSpace()));
            System.out.println("   Usable space: " + formatBytes(store.getUsableSpace()));
            System.out.println();
            
            // Demonstrate polymorphism
            System.out.println("11. Polymorphism demonstration:");
            System.out.println("   Provider class: " + provider.getClass().getSimpleName());
            System.out.println("   FileSystem class: " + fs.getClass().getSimpleName());
            System.out.println("   Path class: " + file1.getClass().getSimpleName());
            System.out.println("   All extend abstract base classes!");
            System.out.println();
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
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
    
    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}
